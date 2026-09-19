package com.mtrnav.compat;

import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.data.Station;
import org.mtr.core.operation.ArrivalResponse;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.ArrivalsCacheClient;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * EVERY direct reference to an MTR class in this whole mod lives in this one
 * file. MTR 4.x has no published/stable addon API -- what follows was
 * verified by decompiling MTR's own 4.0.6 branch (the closest source
 * available while building this) with {@code javap}, specifically:
 * <ul>
 *   <li>{@code org.mtr.core.data.Data} / {@code ClientData} -- the synced
 *       client-side world state (stations, platforms, routes, rails, ...).</li>
 *   <li>{@code org.mtr.mod.client.MinecraftClientData} -- MTR's own
 *       singleton holder of the client {@code ClientData}, used internally
 *       by things like its Passenger Information Display renderer.</li>
 *   <li>{@code org.mtr.mod.data.ArrivalsCacheClient} -- the real, live
 *       arrival-prediction feed MTR's own PIDS boards read from.</li>
 * </ul>
 * If MTR 4.0.5's actual jar differs from 4.0.6 in these classes (package
 * moved, method renamed), this is the ONLY file you should need to touch --
 * everything else in MTRNav talks to {@link Station}, {@link Platform} and
 * {@link Route} objects, never to MTR's internals directly. See README.md.
 */
public final class MTRDataBridge {

	private MTRDataBridge() {
	}

	/** True once MTR has synced at least one station to the client. */
	public static boolean isDataAvailable() {
		try {
			return !getClientData().stations.isEmpty();
		} catch (Throwable t) {
			// A world with MTR installed but no railway built yet, or a world
			// that hasn't finished syncing, is a normal state -- not a crash.
			return false;
		}
	}

	public static ObjectArraySet<Station> getStations() {
		return getClientData().stations;
	}

	public static ObjectArraySet<Platform> getPlatforms() {
		return getClientData().platforms;
	}

	public static ObjectArraySet<Route> getRoutes() {
		return getClientData().routes;
	}

	/**
	 * Distance from the player to a station's nearest platform. Stations in
	 * MTR are an area (a bounding box of platforms), not a single point, so
	 * "distance to station" is defined here as distance to its closest
	 * platform -- matching where the player would actually need to walk.
	 */
	public static double closestPlatformDistance(BlockPos playerPos, Station station) {
		double best = Double.MAX_VALUE;
		for (final Platform platform : station.savedRails) {
			final double d = com.mtrnav.util.DistanceUtil.distance(playerPos, platform.getMidPosition());
			if (d < best) {
				best = d;
			}
		}
		return best;
	}

	public static Platform closestPlatform(BlockPos playerPos, Station station) {
		Platform best = null;
		double bestDistance = Double.MAX_VALUE;
		for (final Platform platform : station.savedRails) {
			final double d = com.mtrnav.util.DistanceUtil.distance(playerPos, platform.getMidPosition());
			if (d < bestDistance) {
				bestDistance = d;
				best = platform;
			}
		}
		return best;
	}

	public static String routeName(Route route) {
		// This is the direct equivalent of the spec's "route.name": Route
		// extends NameColorDataBase, whose getName() returns the raw line
		// name string exactly as the player/server configured it (e.g.
		// "Bus 39", "Yontae Line") -- no vehicle-type branching involved.
		return route.getName();
	}

	public static String stationName(Station station) {
		return station.getName();
	}

	/** The final stop of a route in its stored direction -- what the vehicle's destination signage would show (e.g. "Bus 21 to Yorktown"). */
	public static String routeTerminusName(Route route) {
		final ObjectArrayList<RoutePlatformData> stops = route.getRoutePlatforms();
		if (stops == null || stops.isEmpty()) {
			return "";
		}
		final Platform last = stops.get(stops.size() - 1).getPlatform();
		return last == null ? "" : last.getStationName();
	}

	/** Look up a station/platform/route by its MTR-assigned ID -- used to rebuild saved state (journey resume, remembered planner picks) after a relog, since old Java object references don't survive a rejoin. */
	@Nullable
	public static Station findStationById(long id) {
		for (final Station station : getStations()) {
			if (station.getId() == id) {
				return station;
			}
		}
		return null;
	}

	@Nullable
	public static Platform findPlatformById(long id) {
		for (final Platform platform : getPlatforms()) {
			if (platform.getId() == id) {
				return platform;
			}
		}
		return null;
	}

	@Nullable
	public static Route findRouteById(long id) {
		for (final Route route : getRoutes()) {
			if (route.getId() == id) {
				return route;
			}
		}
		return null;
	}

	/**
	 * Live "next train" predictions for a set of platforms, sourced from the
	 * same cache MTR's own in-world Passenger Information Display boards
	 * use. Returns immediately with whatever is cached; the cache itself is
	 * refreshed by MTR's own client tick, MTRNav just reads it.
	 */
	public static List<ArrivalResponse> requestArrivals(Iterable<Long> platformIds) {
		try {
			final LongAVLTreeSet ids = new LongAVLTreeSet();
			platformIds.forEach(id -> ids.add((long) id));
			if (ids.isEmpty()) {
				return Collections.emptyList();
			}
			final ObjectArrayList<ArrivalResponse> result = ArrivalsCacheClient.INSTANCE.requestArrivals(ids);
			return result;
		} catch (Throwable t) {
			return Collections.emptyList();
		}
	}

	/**
	 * Milliseconds until an arrival, adjusted for client/server clock skew the
	 * same way MTR's own PIDS boards do: {@code ArrivalsCache#getMillisOffset()}
	 * is (server time - client time) captured when the prediction was fetched,
	 * so "server time now" is approximated as the client clock plus that offset.
	 */
	public static long etaMillis(ArrivalResponse response) {
		final long estimatedServerNow = System.currentTimeMillis() + ArrivalsCacheClient.INSTANCE.getMillisOffset();
		return Math.max(0, response.getArrival() - estimatedServerNow);
	}

	private static MinecraftClientData getClientData() {
		return MinecraftClientData.getInstance();
	}
}
