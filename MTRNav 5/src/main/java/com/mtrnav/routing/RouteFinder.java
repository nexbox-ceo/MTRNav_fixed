package com.mtrnav.routing;

import com.mtrnav.util.DistanceUtil;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.Station;
import org.mtr.core.data.TransportMode;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs journey searches off the render/client thread. {@link #findRoutes}
 * builds a fresh {@link TransitGraph} from whatever MTR data is currently
 * synced, then runs a modified A* (Dijkstra with an admissible straight-line
 * heuristic) up to three times to produce up to three distinct itineraries,
 * each excluding the line(s) already used by the previous, better option --
 * this is what naturally produces "Route 2 uses different lines" results
 * like the ones in the phone mockup, without needing full k-shortest-paths.
 */
public final class RouteFinder {

	private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(daemonFactory());
	private static final int MAX_ROUTES = 3;

	// Fastest configured transport mode's speed is used as the A* heuristic's
	// speed bound, which keeps the heuristic admissible (never overestimates
	// remaining travel time) even on a network mixing trains, boats and buses.
	private static final double HEURISTIC_METERS_PER_MILLIS = Arrays.stream(TransportMode.values())
			.mapToDouble(mode -> mode.defaultSpeedMetersPerMillisecond)
			.max()
			.orElse(0.02);

	private RouteFinder() {
	}

	private static ThreadFactory daemonFactory() {
		final AtomicInteger count = new AtomicInteger();
		return runnable -> {
			final Thread thread = new Thread(runnable, "mtrnav-routefinder-" + count.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		};
	}

	/**
	 * @param avoidLine optional line the rider never wants to board; walking
	 *                  and every other line remain available.
	 */
	public static CompletableFuture<List<RouteOption>> findRoutes(Station origin, Station destination, @Nullable Route avoidLine) {
		return CompletableFuture.supplyAsync(() -> computeRoutes(origin, destination, avoidLine), EXECUTOR);
	}

	private static List<RouteOption> computeRoutes(Station origin, Station destination, @Nullable Route avoidLine) {
		final TransitGraph graph = TransitGraph.buildLive();
		final Set<Long> sourcePlatforms = platformIds(origin);
		final Set<Long> targetPlatforms = platformIds(destination);
		if (sourcePlatforms.isEmpty() || targetPlatforms.isEmpty()) {
			return List.of();
		}

		final Set<Long> excludedRouteIds = new HashSet<>();
		if (avoidLine != null) {
			excludedRouteIds.add(avoidLine.getId());
		}

		final List<RouteOption> results = new ArrayList<>();
		final Set<Set<Long>> seenLineCombinations = new HashSet<>();

		for (int attempt = 0; attempt < MAX_ROUTES; attempt++) {
			final PathResult path = aStar(graph, sourcePlatforms, targetPlatforms, excludedRouteIds);
			if (path == null) {
				break;
			}
			final RouteOption option = toRouteOption(graph, origin, destination, path);
			final Set<Long> lineCombination = new HashSet<>();
			for (final RouteLeg leg : option.legs) {
				if (!leg.isWalk()) {
					lineCombination.add(leg.route.getId());
				}
			}
			if (!seenLineCombinations.add(lineCombination)) {
				break; // Re-found the same set of lines -- no more distinct alternatives to offer.
			}
			results.add(option);
			excludedRouteIds.addAll(lineCombination);
			if (lineCombination.isEmpty()) {
				break; // Pure-walk result; excluding "nothing" would loop forever.
			}
		}

		results.sort(Comparator.comparingLong(o -> o.totalDurationMillis));
		return results;
	}

	private static Set<Long> platformIds(Station station) {
		final Set<Long> ids = new HashSet<>();
		for (final Platform platform : station.savedRails) {
			ids.add(platform.getId());
		}
		return ids;
	}

	// --- A* / Dijkstra core -------------------------------------------------

	private static final class PathResult {
		final long targetPlatformId;
		final Map<Long, Long> distanceMillis;
		final Map<Long, Long> previousPlatform;
		final Map<Long, GraphEdge> edgeUsed;

		PathResult(long targetPlatformId, Map<Long, Long> distanceMillis, Map<Long, Long> previousPlatform, Map<Long, GraphEdge> edgeUsed) {
			this.targetPlatformId = targetPlatformId;
			this.distanceMillis = distanceMillis;
			this.previousPlatform = previousPlatform;
			this.edgeUsed = edgeUsed;
		}
	}

	@Nullable
	private static PathResult aStar(TransitGraph graph, Set<Long> sources, Set<Long> targets, Set<Long> excludedRouteIds) {
		final Map<Long, Long> distanceMillis = new HashMap<>();
		final Map<Long, Long> previousPlatform = new HashMap<>();
		final Map<Long, GraphEdge> edgeUsed = new HashMap<>();
		final PriorityQueue<long[]> frontier = new PriorityQueue<>(Comparator.comparingLong(entry -> entry[1]));
		// long[] = {platformId, priority (distance + heuristic)}

		for (final long sourceId : sources) {
			distanceMillis.put(sourceId, 0L);
			frontier.add(new long[]{sourceId, heuristic(graph, sourceId, targets)});
		}

		final Set<Long> visited = new HashSet<>();
		while (!frontier.isEmpty()) {
			final long[] current = frontier.poll();
			final long currentId = current[0];
			if (!visited.add(currentId)) {
				continue;
			}
			if (targets.contains(currentId)) {
				return new PathResult(currentId, distanceMillis, previousPlatform, edgeUsed);
			}

			final long currentDistance = distanceMillis.getOrDefault(currentId, Long.MAX_VALUE);
			for (final GraphEdge edge : graph.neighbors(currentId)) {
				if (!edge.isWalk() && excludedRouteIds.contains(edge.route.getId())) {
					continue;
				}
				final long newDistance = currentDistance + edge.weightMillis;
				if (newDistance < distanceMillis.getOrDefault(edge.toPlatformId, Long.MAX_VALUE)) {
					distanceMillis.put(edge.toPlatformId, newDistance);
					previousPlatform.put(edge.toPlatformId, currentId);
					edgeUsed.put(edge.toPlatformId, edge);
					frontier.add(new long[]{edge.toPlatformId, newDistance + heuristic(graph, edge.toPlatformId, targets)});
				}
			}
		}
		return null;
	}

	private static long heuristic(TransitGraph graph, long platformId, Set<Long> targets) {
		final Platform from = graph.platform(platformId);
		if (from == null || targets.isEmpty()) {
			return 0;
		}
		double bestBlocks = Double.MAX_VALUE;
		for (final long targetId : targets) {
			final Platform target = graph.platform(targetId);
			if (target == null) {
				continue;
			}
			final double blocks = DistanceUtil.distance(from.getMidPosition(), target.getMidPosition());
			if (blocks < bestBlocks) {
				bestBlocks = blocks;
			}
		}
		if (bestBlocks == Double.MAX_VALUE) {
			return 0;
		}
		// blocks -> meters is 1:1 in Minecraft's convention MTR itself uses.
		return (long) (bestBlocks / HEURISTIC_METERS_PER_MILLIS);
	}

	// --- Path reconstruction -------------------------------------------------

	private static RouteOption toRouteOption(TransitGraph graph, Station origin, Station destination, PathResult path) {
		final Deque<Long> platformOrder = new ArrayDeque<>();
		final Deque<GraphEdge> edgeOrder = new ArrayDeque<>();
		long cursor = path.targetPlatformId;
		platformOrder.addFirst(cursor);
		while (path.previousPlatform.containsKey(cursor)) {
			edgeOrder.addFirst(path.edgeUsed.get(cursor));
			cursor = path.previousPlatform.get(cursor);
			platformOrder.addFirst(cursor);
		}

		final List<Long> platforms = new ArrayList<>(platformOrder);
		final List<GraphEdge> edges = new ArrayList<>(edgeOrder);
		final List<RouteLeg> legs = new ArrayList<>();

		int i = 0;
		while (i < edges.size()) {
			final GraphEdge firstEdge = edges.get(i);
			final Route legRoute = firstEdge.route; // null => walking leg
			int j = i;
			long legMillis = 0;
			final List<Platform> intermediates = new ArrayList<>();
			while (j < edges.size() && sameLine(edges.get(j).route, legRoute)) {
				legMillis += edges.get(j).weightMillis;
				if (j > i) {
					intermediates.add(graph.platform(platforms.get(j)));
				}
				j++;
			}
			final Platform board = graph.platform(platforms.get(i));
			final Platform alight = graph.platform(platforms.get(j));
			legs.add(new RouteLeg(legRoute, board, alight, intermediates, legMillis));
			i = j;
		}

		return new RouteOption(origin, destination, legs);
	}

	private static boolean sameLine(@Nullable Route a, @Nullable Route b) {
		if (a == null || b == null) {
			return a == b;
		}
		return a.getId() == b.getId();
	}
}
