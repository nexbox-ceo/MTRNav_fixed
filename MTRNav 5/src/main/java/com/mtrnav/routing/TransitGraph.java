package com.mtrnav.routing;

import com.mtrnav.compat.MTRDataBridge;
import com.mtrnav.util.DistanceUtil;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.data.Station;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.ArrayList;
import java.util.List;

/**
 * A snapshot of MTR's live station/platform/route data, reshaped into a
 * directed weighted graph of platforms so {@link RouteFinder} can run
 * Dijkstra/A* over it. Rebuilt fresh for every journey search (the network
 * is small enough per-world that this is cheap, and it guarantees results
 * always reflect the current railway rather than a stale cache).
 *
 * Two kinds of edge:
 *  - "Ride" edges, one per consecutive stop pair on every {@link Route},
 *    weighted by that route's recorded segment duration.
 *  - "Walk" edges, connecting every platform in a {@link Station} to every
 *    other platform in the same station, weighted by walking time -- this
 *    is what lets a rider "change lines" at an interchange station.
 */
public final class TransitGraph {

	// Roughly vanilla player walking speed, blocks/second, used only to turn
	// in-station transfer distances into a time estimate for the graph.
	private static final double WALK_BLOCKS_PER_SECOND = 4.3;
	private static final long DEFAULT_SEGMENT_MILLIS = 60_000L;

	private final Long2ObjectOpenHashMap<Platform> platformsById = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectOpenHashMap<List<GraphEdge>> adjacency = new Long2ObjectOpenHashMap<>();

	public static TransitGraph buildLive() {
		return new TransitGraph();
	}

	private TransitGraph() {
		for (final Platform platform : MTRDataBridge.getPlatforms()) {
			platformsById.put(platform.getId(), platform);
			adjacency.put(platform.getId(), new ArrayList<>());
		}
		for (final Route route : MTRDataBridge.getRoutes()) {
			addRideEdges(route);
		}
		for (final Station station : MTRDataBridge.getStations()) {
			addTransferEdges(station);
		}
	}

	private void addRideEdges(Route route) {
		final ObjectArrayList<RoutePlatformData> stops = route.getRoutePlatforms();
		if (stops == null || stops.size() < 2) {
			return;
		}
		// ASSUMPTION (flagged in README): Route#durations holds one entry per
		// consecutive stop pair, i.e. durations.size() == stops.size() - 1,
		// in milliseconds. If your MTR build's durations don't line up this
		// way, every ride edge below silently falls back to a flat default
		// instead of crashing -- routes will still generate, just with a
		// less accurate ETA.
		final boolean haveDurations = route.durations != null && route.durations.size() == stops.size() - 1;
		for (int i = 0; i < stops.size() - 1; i++) {
			final Platform from = stops.get(i).getPlatform();
			final Platform to = stops.get(i + 1).getPlatform();
			if (from == null || to == null) {
				continue;
			}
			final long weight = haveDurations ? route.durations.getLong(i) : DEFAULT_SEGMENT_MILLIS;
			edgesFrom(from.getId()).add(new GraphEdge(to.getId(), route, weight));
		}
	}

	private void addTransferEdges(Station station) {
		final List<Platform> platforms = new ArrayList<>(station.savedRails);
		for (int i = 0; i < platforms.size(); i++) {
			for (int j = 0; j < platforms.size(); j++) {
				if (i == j) {
					continue;
				}
				final Platform a = platforms.get(i);
				final Platform b = platforms.get(j);
				final double blocks = DistanceUtil.distance(a.getMidPosition(), b.getMidPosition());
				final long millis = Math.max(5_000L, (long) (blocks / WALK_BLOCKS_PER_SECOND * 1000.0));
				edgesFrom(a.getId()).add(new GraphEdge(b.getId(), null, millis));
			}
		}
	}

	private List<GraphEdge> edgesFrom(long platformId) {
		return adjacency.computeIfAbsent(platformId, id -> new ArrayList<>());
	}

	public List<GraphEdge> neighbors(long platformId) {
		return adjacency.getOrDefault(platformId, List.of());
	}

	public Platform platform(long platformId) {
		return platformsById.get(platformId);
	}

	public boolean hasPlatform(long platformId) {
		return platformsById.containsKey(platformId);
	}
}
