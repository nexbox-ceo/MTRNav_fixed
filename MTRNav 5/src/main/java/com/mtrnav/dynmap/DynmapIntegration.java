package com.mtrnav.dynmap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.dynmap.DynmapCommonAPI;
import org.dynmap.DynmapCommonAPIListener;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import org.mtr.mod.Init;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Puts MTR stations on Dynmap's web map. Runs entirely server-side (this is
 * loaded from {@link com.mtrnav.MTRNavMod}, the common entrypoint) and is a
 * pure soft dependency: every class here is {@code org.dynmap.*}, and this
 * class is only ever touched from behind a
 * {@code FabricLoader.isModLoaded("dynmap")} check, so a server without
 * Dynmap installed never loads it and never needs its classes to exist.
 *
 * <p><b>What's verified vs. what needs a quick check on your server</b> --
 * this build could not run an actual Fabric/MTR/Dynmap server to test
 * end-to-end, so two things are marked below:
 * <ul>
 *   <li><b>Verified against the real jars</b>: MTR 4.0.5 embeds its own
 *       Jetty webserver (port from {@code Init.getServerPort()}) serving a
 *       JSON map API at exactly the path {@code /mtr/api/map/*}, with an
 *       {@code endpoint=stations-and-routes} option and a {@code dimension}
 *       parameter -- these exact strings were found as constants inside
 *       {@code org.mtr.core.servlet.SystemMapServlet} in the jar you
 *       uploaded, not guessed. Dynmap's {@code MarkerAPI} calls used below
 *       are likewise verified against the Dynmap jar you uploaded.</li>
 *   <li><b>Needs your own 30-second check</b>: the exact JSON key names
 *       inside that response for a station's name/position weren't
 *       something this pass could confirm (that needs decompiling the
 *       servlet's method body, not just its signature). {@link #extractStations}
 *       below tries several plausible key names defensively and simply
 *       finds zero stations if none match -- it will never crash the
 *       server. Run {@code curl "http://localhost:<port>/mtr/api/map/stations-and-routes?dimension=<id>"}
 *       while your world is open, look at the real JSON, and adjust the
 *       key names in {@link #extractStations} to match.</li>
 * </ul>
 */
public final class DynmapIntegration {

	private static final String MARKER_SET_ID = "mtrnav.stations";
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

	private static ScheduledExecutorService scheduler;
	private static MarkerSet markerSet;
	private static MinecraftServer server;

	private DynmapIntegration() {
	}

	public static boolean isDynmapPresent() {
		return FabricLoader.getInstance().isModLoaded("dynmap");
	}

	public static void register(MinecraftServer minecraftServer) {
		server = minecraftServer;
		DynmapCommonAPIListener.register(new DynmapCommonAPIListener() {
			@Override
			public void apiEnabled(DynmapCommonAPI api) {
				final MarkerAPI markerAPI = api.getMarkerAPI();
				markerSet = markerAPI.getMarkerSet(MARKER_SET_ID);
				if (markerSet == null) {
					markerSet = markerAPI.createMarkerSet(MARKER_SET_ID, "MTR Stations", null, false);
				}
				startPolling();
			}

			@Override
			public void apiDisabled(DynmapCommonAPI api) {
				stopPolling();
			}
		});
	}

	private static void startPolling() {
		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			final Thread thread = new Thread(r, "mtrnav-dynmap-sync");
			thread.setDaemon(true);
			return thread;
		});
		scheduler.scheduleWithFixedDelay(DynmapIntegration::pollOnce, 5, 30, TimeUnit.SECONDS);
	}

	private static void stopPolling() {
		if (scheduler != null) {
			scheduler.shutdownNow();
			scheduler = null;
		}
	}

	private static void pollOnce() {
		if (markerSet == null || server == null) {
			return;
		}
		final int port = Init.getServerPort();
		final Set<String> keptMarkerIds = new HashSet<>();

		for (final ServerWorld world : server.getWorlds()) {
			final String dimensionId = Init.getWorldId(new org.mtr.mapping.holder.World(world));
			final String dynmapWorldName = world.getRegistryKey().getValue().toString();
			try {
				final String url = "http://127.0.0.1:" + port + "/mtr/api/map/stations-and-routes?dimension=" + dimensionId;
				final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(2)).GET().build();
				final HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() != 200) {
					continue;
				}
				for (final StationMarker station : extractStations(response.body())) {
					final String markerId = "mtrnav-" + dimensionId + "-" + station.name;
					keptMarkerIds.add(markerId);
					Marker marker = markerSet.findMarker(markerId);
					if (marker == null) {
						markerSet.createMarker(markerId, station.name, dynmapWorldName, station.x, station.y, station.z, null, false);
					} else {
						marker.setLocation(dynmapWorldName, station.x, station.y, station.z);
						marker.setLabel(station.name);
					}
				}
			} catch (Exception ignored) {
				// MTR's webserver not reachable / JSON shape unexpected -- skip this poll, try again next cycle.
			}
		}

		for (final Marker marker : new HashSet<>(markerSet.getMarkers())) {
			if (marker.getMarkerID().startsWith("mtrnav-") && !keptMarkerIds.contains(marker.getMarkerID())) {
				marker.deleteMarker();
			}
		}
	}

	private record StationMarker(String name, double x, double y, double z) {
	}

	/** See the "needs your own check" note in the class doc. */
	private static Set<StationMarker> extractStations(String json) {
		final Set<StationMarker> stations = new HashSet<>();
		try {
			final JsonElement root = JsonParser.parseString(json);
			if (!root.isJsonObject()) {
				return stations;
			}
			final JsonElement stationsElement = root.getAsJsonObject().get("stations");
			if (stationsElement == null || !stationsElement.isJsonArray()) {
				return stations;
			}
			stationsElement.getAsJsonArray().forEach(element -> {
				if (!element.isJsonObject()) {
					return;
				}
				final JsonObject obj = element.getAsJsonObject();
				final String name = firstString(obj, "name", "stationName");
				final double[] xyz = firstPosition(obj);
				if (name != null && xyz != null) {
					stations.add(new StationMarker(name, xyz[0], xyz[1], xyz[2]));
				}
			});
		} catch (Exception ignored) {
			// Malformed/unexpected JSON -- treated the same as "no stations this poll".
		}
		return stations;
	}

	private static String firstString(JsonObject obj, String... keys) {
		for (final String key : keys) {
			if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
				return obj.get(key).getAsString();
			}
		}
		return null;
	}

	private static double[] firstPosition(JsonObject obj) {
		// Try flat x/y/z first, then a couple of plausible nested shapes.
		if (obj.has("x") && obj.has("z")) {
			return new double[]{obj.get("x").getAsDouble(), obj.has("y") ? obj.get("y").getAsDouble() : 64, obj.get("z").getAsDouble()};
		}
		for (final String key : new String[]{"position", "center", "position1"}) {
			if (obj.has(key) && obj.get(key).isJsonObject()) {
				final JsonObject pos = obj.getAsJsonObject(key);
				if (pos.has("x") && pos.has("z")) {
					return new double[]{pos.get("x").getAsDouble(), pos.has("y") ? pos.get("y").getAsDouble() : 64, pos.get("z").getAsDouble()};
				}
			}
		}
		return null;
	}
}
