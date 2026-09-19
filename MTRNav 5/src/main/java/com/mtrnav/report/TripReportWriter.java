package com.mtrnav.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Writes a big, fun, Minecraft-crash-report-styled summary of a completed trip to disk. */
public final class TripReportWriter {

	private static final List<String> WITTY_LINES = List.of(
			"Mind the gap between platform and reality.",
			"You have reached your destination. Please mind the step down into your regular life.",
			"All aboard the hype train, which was, legally speaking, a Bus 21.",
			"Achievement unlocked: Didn't miss the stop.",
			"Local legend defeats public transport once again.",
			"The driver said nothing. The driver never says anything.",
			"Somewhere, a timetable felt seen.",
			"You are now free to move about the station.",
			"This journey has been brought to you by the number of stops you just sat through.",
			"Survived the ride. Emeralds optional. Legroom not guaranteed.",
			"Congratulations, you have out-traveled at least one villager today.",
			"Somewhere, a signal box is very proud of itself right now.",
			"This report is 100% blockchain-free public transport data.",
			"No creepers were harmed in the making of this journey. Probably.",
			"You've been riding trains that don't even have a Nether portal. Respect.",
			"Breaking: local player takes bus instead of just flying with Elytra like a normal menace."
	);

	private static final List<String> STOP_FLAVOR = List.of(
			"nobody got on, nobody got off, everyone just vibed",
			"someone definitely should have gotten off here and didn't",
			"a brief, contractually obligated pause",
			"the doors opened for what can only be described as a formality",
			"this stop exists and that's about all that can be said for it",
			"a villager looked at the train and chose not to get involved",
			"the announcement system said something, technically",
			"peak commuter energy: everyone stared at the floor",
			"this is the stop equivalent of a participation trophy",
			"someone's inventory was definitely full and they panic-jumped off here"
	);

	private static final List<String> LATE_REASONS = List.of(
			"A creeper wandered onto the tracks and had to be politely asked to leave.",
			"Someone held the doors open for a chicken. The chicken did not appreciate it.",
			"Leaves on the line. In a game with no autumn. Nobody has explained this.",
			"The driver stopped to haggle with a villager over emerald prices.",
			"Signal failure near the junction, caused by person or persons unknown (it was probably a bat).",
			"A passenger asked \"is this the right train?\" four stops in.",
			"The train paused for a scenic view of absolutely nothing in particular.",
			"Points failure, believed to be caused by a zombie with main character energy.",
			"Someone tried to sleep in a bed on board. This is not how beds work.",
			"The driver got distracted mining a block that was clearly load-bearing.",
			"A skeleton fired an arrow at the train for reasons that remain unclear.",
			"There was, allegedly, a cow on the line. There is always, allegedly, a cow on the line."
	);

	private static final List<String> ON_TIME_REASONS = List.of(
			"Everything just... worked. No notes.",
			"The driver was clearly having an excellent day.",
			"MTRNav suspects sorcery, but isn't going to question it.",
			"A rare and beautiful alignment of timetable and reality.",
			"Somewhere, a signal engineer is taking full credit for this.",
			"Ran so smoothly it was almost suspicious.",
			"No creepers, no cows on the line, no drama. A perfect run.",
			"This is what happens when nobody argues with the villager about ticket prices."
	);

	// Each is run through String.format with the destination name -- the ones
	// without a "%s" just ignore the argument, so both plain and
	// destination-aware sign-offs can share one pool.
	private static final List<String> SIGN_OFFS = List.of(
			"Until next time, keep your hands, feet, and ender pearls inside the vehicle.",
			"MTRNav: getting you there, eventually, mostly on purpose.",
			"This has been a public service announcement from a phone that shouldn't exist in a train yet somehow does.",
			"Please rate your journey by shouting into the void. The void does not currently accept feedback.",
			"Thank you for choosing rails over recklessly sprinting through a ravine.",
			"Safe travels, and please don't feed the villagers at the platform.",
			"Welcome to %s.",
			"That's the trip. Welcome to %s.",
			"MTRNav delivers again. Welcome to %s.",
			"Doors closing, journey ending, destination reached. Welcome to %s."
	);

	private TripReportWriter() {
	}

	/**
	 * Saved to {@code Documents/MTRNav Trip Reports/} in your actual user
	 * home folder -- not the Minecraft instance's own directory -- so it
	 * shows up somewhere you'd naturally go looking for it, not buried in a
	 * game folder. Uses the plain {@code user.home}/Documents convention,
	 * which is right for a default setup on Windows/macOS/Linux, but won't
	 * follow a Documents folder you've manually relocated (e.g. Windows
	 * folder redirection to OneDrive) -- if reports don't show up where
	 * expected, check {@link #reportsDirectory()}'s printed path.
	 */
	private static Path reportsDirectory() {
		return Path.of(System.getProperty("user.home"), "Documents", "MTRNav Trip Reports");
	}

	/** Returns the saved file path, or empty on failure (never throws). */
	public static Optional<Path> save(TripSummary summary) {
		try {
			final Path dir = reportsDirectory();
			Files.createDirectories(dir);
			final String filename = "trip-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()) + ".txt";
			final Path file = dir.resolve(filename);
			Files.writeString(file, format(summary));
			return Optional.of(file);
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	private static String format(TripSummary summary) {
		final Random random = new Random();
		final boolean ranLate = summary.actualMinutes > summary.plannedMinutes;
		final long minutesOff = Math.abs(summary.actualMinutes - summary.plannedMinutes);
		final String timingReason = ranLate
				? LATE_REASONS.get(random.nextInt(LATE_REASONS.size()))
				: ON_TIME_REASONS.get(random.nextInt(ON_TIME_REASONS.size()));

		final StringBuilder sb = new StringBuilder();
		sb.append("---- MTRNav Trip Report ----\n");
		sb.append("// ").append(WITTY_LINES.get(random.nextInt(WITTY_LINES.size()))).append("\n\n");
		sb.append("Report saved: ").append(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now())).append("\n");
		sb.append("Description: Made it from ").append(summary.originName).append(" to ").append(summary.destinationName).append(".\n\n");

		sb.append("-- Trip Overview --\n");
		sb.append("Details:\n");
		sb.append("\tTrip date: ").append(summary.tripDate).append("\n");
		sb.append("\tOrigin: ").append(summary.originName).append("\n");
		sb.append("\tDestination: ").append(summary.destinationName).append("\n");
		sb.append("\tLines used: ").append(summary.lineSummary.isEmpty() ? "(walked the whole way)" : summary.lineSummary).append("\n");
		sb.append("\tLegs: ").append(summary.legs.size()).append("\n");
		sb.append("\tStops passed through (not yours): ").append(summary.totalStopsPassed()).append("\n\n");

		// This is the actual trip's departure/arrival, NOT when this file was
		// saved -- those can be hours apart if you don't hit Save right away,
		// which is exactly the confusing bug this section exists to avoid.
		sb.append("-- Timings (this is when the trip actually happened) --\n");
		sb.append("Details:\n");
		sb.append("\tDeparted: ").append(summary.tripDate).append(" ").append(summary.departureClock).append("\n");
		sb.append("\tPlanned arrival: ").append(summary.plannedArrivalClock).append("\n");
		sb.append("\tActual arrival: ").append(summary.actualArrivalClock).append("\n");
		sb.append("\tPlanned duration: ").append(summary.plannedMinutes).append(" minute(s)\n");
		sb.append("\tActual duration: ").append(summary.actualMinutes).append(" minute(s)\n\n");

		sb.append("-- Why The Timing Was What It Was --\n");
		sb.append("Details:\n");
		if (minutesOff == 0) {
			sb.append("\tDead on time. ").append(timingReason).append("\n\n");
		} else {
			sb.append("\t").append(ranLate ? "Ran " + minutesOff + " minute(s) late." : "Finished " + minutesOff + " minute(s) early.").append(" ").append(timingReason).append("\n\n");
		}

		sb.append("-- Leg-by-Leg Breakdown --\n");
		for (int i = 0; i < summary.legs.size(); i++) {
			final TripSummary.LegSummary leg = summary.legs.get(i);
			sb.append("Leg ").append(i + 1).append(": ");
			if (leg.isWalk) {
				sb.append("Walked from ").append(leg.boardName).append(" to ").append(leg.alightName)
						.append(" (").append(millisToMinutes(leg.durationMillis)).append(" min)\n");
			} else {
				sb.append(leg.lineName).append(leg.isBus ? " (bus)" : "").append(" -- boarded at ").append(leg.boardName)
						.append(", got off at ").append(leg.alightName)
						.append(" (").append(millisToMinutes(leg.durationMillis)).append(" min)\n");
				sb.append("Details:\n");
				sb.append("\tLine terminates at: ").append(leg.terminusName.isEmpty() ? "(unknown)" : leg.terminusName).append("\n");
				if (leg.passedStopNames.isEmpty()) {
					sb.append("\tStops passed without getting off: none -- straight through\n");
				} else {
					sb.append("\tStops passed without getting off (").append(leg.passedStopNames.size()).append("):\n");
					for (final String stop : leg.passedStopNames) {
						sb.append("\t\t- ").append(stop).append(" (").append(STOP_FLAVOR.get(random.nextInt(STOP_FLAVOR.size()))).append(")\n");
					}
				}
			}
			sb.append("\n");
		}

		sb.append("-- Sign-Off --\n");
		sb.append("Details:\n");
		sb.append("\t").append(String.format(java.util.Locale.ROOT, SIGN_OFFS.get(random.nextInt(SIGN_OFFS.size())), summary.destinationName)).append("\n");
		return sb.toString();
	}

	private static long millisToMinutes(long millis) {
		return Math.max(0, Math.round(millis / 60_000.0));
	}
}
