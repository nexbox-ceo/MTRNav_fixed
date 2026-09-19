package com.mtrnav.phone.apps;

import com.mtrnav.compat.MTRDataBridge;
import net.fabricmc.loader.api.FabricLoader;
import org.mtr.core.data.Route;
import org.mtr.core.data.Station;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Remembers the last-used Origin/Destination/Avoid picks across phone
 * sessions (including relogs) -- stored by MTR object ID, resolved back to
 * live objects via {@link MTRDataBridge} on load, same reasoning as
 * {@link com.mtrnav.journey.JourneyPersistence}.
 */
final class PlannerPrefs {

	private static final Path FILE_PATH = FabricLoader.getInstance().getConfigDir().resolve("mtrnav-planner.properties");

	private PlannerPrefs() {
	}

	static void save(@Nullable Station origin, @Nullable Station destination, @Nullable Route avoidLine) {
		final Properties properties = new Properties();
		if (origin != null) {
			properties.setProperty("origin", Long.toString(origin.getId()));
		}
		if (destination != null) {
			properties.setProperty("destination", Long.toString(destination.getId()));
		}
		if (avoidLine != null) {
			properties.setProperty("avoid", Long.toString(avoidLine.getId()));
		}
		try {
			Files.createDirectories(FILE_PATH.getParent());
			try (OutputStream out = Files.newOutputStream(FILE_PATH)) {
				properties.store(out, "MTRNav planner: last-used Origin/Destination/Avoid");
			}
		} catch (IOException ignored) {
			// Not fatal -- worst case the picks just don't carry over next time.
		}
	}

	static final class Loaded {
		@Nullable
		final Station origin;
		@Nullable
		final Station destination;
		@Nullable
		final Route avoidLine;

		Loaded(@Nullable Station origin, @Nullable Station destination, @Nullable Route avoidLine) {
			this.origin = origin;
			this.destination = destination;
			this.avoidLine = avoidLine;
		}
	}

	/** Returns nulls for anything that no longer resolves (e.g. a station was removed) rather than failing outright. */
	static Loaded load() {
		if (!Files.exists(FILE_PATH)) {
			return new Loaded(null, null, null);
		}
		final Properties properties = new Properties();
		try (InputStream in = Files.newInputStream(FILE_PATH)) {
			properties.load(in);
		} catch (IOException ignored) {
			return new Loaded(null, null, null);
		}
		final Station origin = resolveStation(properties.getProperty("origin"));
		final Station destination = resolveStation(properties.getProperty("destination"));
		final Route avoidLine = resolveRoute(properties.getProperty("avoid"));
		return new Loaded(origin, destination, avoidLine);
	}

	@Nullable
	private static Station resolveStation(String idString) {
		if (idString == null) {
			return null;
		}
		try {
			return MTRDataBridge.findStationById(Long.parseLong(idString));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	@Nullable
	private static Route resolveRoute(String idString) {
		if (idString == null) {
			return null;
		}
		try {
			return MTRDataBridge.findRouteById(Long.parseLong(idString));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
