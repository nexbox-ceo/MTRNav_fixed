package com.mtrnav.report;

import com.mtrnav.compat.MTRDataBridge;
import com.mtrnav.journey.JourneyManager;
import com.mtrnav.routing.RouteLeg;
import com.mtrnav.routing.RouteOption;
import org.mtr.core.data.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A snapshot of a just-finished journey, captured in {@code onArrived()}
 * before {@link JourneyManager#stop()} clears the live state -- includes
 * every leg's board/alight/terminus and every intermediate stop passed
 * through but not gotten off at, for the full trip report.
 */
public final class TripSummary {

	public final String originName;
	public final String destinationName;
	public final String lineSummary;
	public final int plannedMinutes;
	public final long actualMinutes;
	public final String departureClock;
	public final String plannedArrivalClock;
	public final String actualArrivalClock;
	public final String tripDate;
	public final List<LegSummary> legs;

	private TripSummary(String originName, String destinationName, String lineSummary, int plannedMinutes,
						 long actualMinutes, String departureClock, String plannedArrivalClock, String actualArrivalClock,
						 String tripDate, List<LegSummary> legs) {
		this.originName = originName;
		this.destinationName = destinationName;
		this.lineSummary = lineSummary;
		this.plannedMinutes = plannedMinutes;
		this.actualMinutes = actualMinutes;
		this.departureClock = departureClock;
		this.plannedArrivalClock = plannedArrivalClock;
		this.actualArrivalClock = actualArrivalClock;
		this.tripDate = tripDate;
		this.legs = legs;
	}

	public int totalStopsPassed() {
		int total = 0;
		for (final LegSummary leg : legs) {
			total += leg.passedStopNames.size();
		}
		return total;
	}

	public static TripSummary capture(RouteOption route, JourneyManager journey) {
		final long departureRealMillis = journey.getDepartureRealMillis();
		final long actualElapsedMillis = System.currentTimeMillis() - departureRealMillis;
		final List<LegSummary> legSummaries = new ArrayList<>();
		for (final RouteLeg leg : route.legs) {
			legSummaries.add(LegSummary.from(leg));
		}
		final String tripDate = java.time.Instant.ofEpochMilli(departureRealMillis)
				.atZone(java.time.ZoneId.systemDefault())
				.toLocalDate()
				.toString();
		return new TripSummary(
				MTRDataBridge.stationName(route.origin),
				MTRDataBridge.stationName(route.destination),
				route.summary(),
				route.totalMinutes(),
				Math.round(actualElapsedMillis / 60_000.0),
				journey.getDepartureClockLabel(),
				journey.getArrivalClockLabel(),
				java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
				tripDate,
				legSummaries
		);
	}

	/** One leg's full detail: what you rode (or walked), where it ends up terminating, and every stop it passed through that wasn't yours. */
	public static final class LegSummary {
		public final boolean isWalk;
		public final boolean isBus;
		public final String lineName;
		public final String boardName;
		public final String alightName;
		public final String terminusName;
		public final List<String> passedStopNames;
		public final long durationMillis;

		private LegSummary(boolean isWalk, boolean isBus, String lineName, String boardName, String alightName, String terminusName,
							List<String> passedStopNames, long durationMillis) {
			this.isWalk = isWalk;
			this.isBus = isBus;
			this.lineName = lineName;
			this.boardName = boardName;
			this.alightName = alightName;
			this.terminusName = terminusName;
			this.passedStopNames = passedStopNames;
			this.durationMillis = durationMillis;
		}

		static LegSummary from(RouteLeg leg) {
			final List<String> passed = new ArrayList<>();
			for (final Platform stop : leg.intermediateStops) {
				passed.add(stop.getName());
			}
			if (leg.isWalk()) {
				return new LegSummary(true, false, "Walk", leg.boardPlatform.getName(), leg.alightPlatform.getName(), "", passed, leg.durationMillis);
			}
			// The mod has no real "is this a bus" flag to check (see MTRDataBridge's
			// class doc -- routeName() just returns MTR's raw name string, nothing
			// about vehicle type). This is a naming-convention guess, nothing more:
			// if whoever built the line put "bus" in its name, it's treated as one.
			final boolean isBus = leg.lineName().toLowerCase(Locale.ROOT).contains("bus");
			return new LegSummary(false, isBus, leg.lineName(), leg.boardPlatform.getName(), leg.alightPlatform.getName(),
					MTRDataBridge.routeTerminusName(leg.route), passed, leg.durationMillis);
		}
	}
}
