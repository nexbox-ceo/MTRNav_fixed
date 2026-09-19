package com.mtrnav.report;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalDouble;
import java.util.Properties;

/**
 * A running 1-5 rating average across every journey you've bothered to
 * rate, stored as just a count and a running sum (not the individual
 * ratings themselves) -- enough to compute a mean, nothing more to keep
 * around. The average only shows once {@link #MIN_RATINGS_FOR_AVERAGE}
 * trips have been rated, so one bad (or one glowing) early rating doesn't
 * look like a verdict on its own.
 */
public final class FeedbackStore {

	public static final int MIN_RATINGS_FOR_AVERAGE = 5;

	private static final Path FILE_PATH = FabricLoader.getInstance().getConfigDir().resolve("mtrnav-feedback.properties");

	private FeedbackStore() {
	}

	public static void recordRating(int rating) {
		final int clamped = Math.max(1, Math.min(5, rating));
		final long[] state = load();
		state[0] += 1;
		state[1] += clamped;
		save(state[0], state[1]);
	}

	public static int getCount() {
		return (int) load()[0];
	}

	/** Empty until {@link #MIN_RATINGS_FOR_AVERAGE} ratings have been recorded. */
	public static OptionalDouble getAverage() {
		final long[] state = load();
		if (state[0] < MIN_RATINGS_FOR_AVERAGE) {
			return OptionalDouble.empty();
		}
		return OptionalDouble.of((double) state[1] / state[0]);
	}

	private static long[] load() {
		final Properties properties = new Properties();
		if (Files.exists(FILE_PATH)) {
			try (InputStream in = Files.newInputStream(FILE_PATH)) {
				properties.load(in);
			} catch (IOException ignored) {
				// Start from zero on a corrupt/unreadable file.
			}
		}
		final long count = parseLongOrZero(properties.getProperty("count"));
		final long sum = parseLongOrZero(properties.getProperty("sum"));
		return new long[]{count, sum};
	}

	private static void save(long count, long sum) {
		final Properties properties = new Properties();
		properties.setProperty("count", Long.toString(count));
		properties.setProperty("sum", Long.toString(sum));
		try {
			Files.createDirectories(FILE_PATH.getParent());
			try (OutputStream out = Files.newOutputStream(FILE_PATH)) {
				properties.store(out, "MTRNav trip feedback: running count/sum of 1-5 ratings");
			}
		} catch (IOException ignored) {
			// Not fatal -- worst case this rating doesn't count towards the average.
		}
	}

	private static long parseLongOrZero(String value) {
		try {
			return value == null ? 0 : Long.parseLong(value);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
