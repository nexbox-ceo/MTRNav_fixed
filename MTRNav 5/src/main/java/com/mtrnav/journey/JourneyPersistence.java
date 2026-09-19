package com.mtrnav.journey;

import com.google.gson.Gson;
import com.mtrnav.compat.MTRDataBridge;
import com.mtrnav.routing.RouteLeg;
import com.mtrnav.routing.RouteOption;
import net.fabricmc.loader.api.FabricLoader;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.Station;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves the active journey to disk (by MTR object ID, not Java object
 * reference -- old references don't survive a relog since MTR rebuilds its
 * client data fresh each session) so it survives a disconnect/relog.
 * Client-side only, single local file: this is a personal-world feature,
 * not a per-server-account one.
 */
public final class JourneyPersistence {

	private static final Path FILE_PATH = FabricLoader.getInstance().getConfigDir().resolve("mtrnav-journey.json");
	private static final Gson GSON = new Gson();

	private JourneyPersistence() {
	}

	public static void save(JourneyManager journey) {
		final RouteOption route = journey.getActiveRoute();
		if (route == null || journey.getPhase() == null) {
			return;
		}
		final SaveState state = new SaveState();
		state.originStationId = route.origin.getId();
		state.destinationStationId = route.destination.getId();
		state.legs = new ArrayList<>();
		for (final RouteLeg leg : route.legs) {
			final LegSave legSave = new LegSave();
			legSave.routeId = leg.isWalk() ? null : leg.route.getId();
			legSave.boardPlatformId = leg.boardPlatform.getId();
			legSave.alightPlatformId = leg.alightPlatform.getId();
			legSave.intermediateStopIds = new ArrayList<>();
			for (final Platform stop : leg.intermediateStops) {
				legSave.intermediateStopIds.add(stop.getId());
			}
			legSave.durationMillis = leg.durationMillis;
			state.legs.add(legSave);
		}
		state.legIndex = journey.getLegIndex();
		state.phase = journey.getPhase().name();
		state.departureRealMillis = journey.getDepartureRealMillis();
		state.departureClockLabel = journey.getDepartureClockLabel();
		state.arrivalClockLabel = journey.getArrivalClockLabel();
		state.nextStopIndex = journey.getNextStopIndex();
		try {
			Files.createDirectories(FILE_PATH.getParent());
			Files.writeString(FILE_PATH, GSON.toJson(state));
		} catch (IOException ignored) {
			// Not fatal -- worst case a relog mid-journey just doesn't resume.
		}
	}

	public static void clear() {
		try {
			Files.deleteIfExists(FILE_PATH);
		} catch (IOException ignored) {
			// Harmless -- a stale save just gets overwritten or fails to restore next time.
		}
	}

	public static boolean hasSavedJourney() {
		return Files.exists(FILE_PATH);
	}

	/**
	 * Call once per world-join, after {@link MTRDataBridge#isDataAvailable()}
	 * is true (station/platform/route IDs won't resolve before then).
	 * Returns true if a journey was restored; always clears the save file
	 * either way so a bad/unresolvable save doesn't retry forever.
	 */
	public static boolean tryRestore(JourneyManager journey) {
		if (!Files.exists(FILE_PATH)) {
			return false;
		}
		try {
			final String json = Files.readString(FILE_PATH);
			final SaveState state = GSON.fromJson(json, SaveState.class);
			final Station origin = MTRDataBridge.findStationById(state.originStationId);
			final Station destination = MTRDataBridge.findStationById(state.destinationStationId);
			if (origin == null || destination == null || state.legs == null || state.legs.isEmpty()) {
				return false;
			}
			final List<RouteLeg> legs = new ArrayList<>();
			for (final LegSave legSave : state.legs) {
				final Platform board = MTRDataBridge.findPlatformById(legSave.boardPlatformId);
				final Platform alight = MTRDataBridge.findPlatformById(legSave.alightPlatformId);
				if (board == null || alight == null) {
					return false;
				}
				Route route = null;
				if (legSave.routeId != null) {
					route = MTRDataBridge.findRouteById(legSave.routeId);
					if (route == null) {
						return false;
					}
				}
				final List<Platform> intermediates = new ArrayList<>();
				for (final long stopId : legSave.intermediateStopIds) {
					final Platform stop = MTRDataBridge.findPlatformById(stopId);
					if (stop == null) {
						return false;
					}
					intermediates.add(stop);
				}
				legs.add(new RouteLeg(route, board, alight, intermediates, legSave.durationMillis));
			}
			JourneyPhase phase;
			try {
				phase = JourneyPhase.valueOf(state.phase);
			} catch (Exception e) {
				phase = JourneyPhase.WALKING;
			}
			journey.resume(new RouteOption(origin, destination, legs), state.legIndex, phase,
					state.departureRealMillis, state.departureClockLabel, state.arrivalClockLabel, state.nextStopIndex);
			return true;
		} catch (Exception ignored) {
			return false;
		} finally {
			clear(); // avoid retrying a bad save forever; resume() immediately re-saves valid state itself
		}
	}

	private static final class SaveState {
		long originStationId;
		long destinationStationId;
		List<LegSave> legs;
		int legIndex;
		String phase;
		long departureRealMillis;
		String departureClockLabel;
		String arrivalClockLabel;
		int nextStopIndex;
	}

	private static final class LegSave {
		Long routeId;
		long boardPlatformId;
		long alightPlatformId;
		List<Long> intermediateStopIds;
		long durationMillis;
	}
}
