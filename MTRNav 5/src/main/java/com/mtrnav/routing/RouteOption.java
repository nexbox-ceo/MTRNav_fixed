package com.mtrnav.routing;

import org.mtr.core.data.Station;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** One complete origin-to-destination journey: an ordered list of legs. */
public final class RouteOption {

	public final Station origin;
	public final Station destination;
	public final List<RouteLeg> legs;
	public final long totalDurationMillis;

	public RouteOption(Station origin, Station destination, List<RouteLeg> legs) {
		this.origin = origin;
		this.destination = destination;
		this.legs = legs;
		long total = 0;
		for (final RouteLeg leg : legs) {
			total += leg.durationMillis;
		}
		this.totalDurationMillis = total;
	}

	public int totalMinutes() {
		return (int) Math.round(totalDurationMillis / 60_000.0);
	}

	/** Line names actually ridden, in order, de-duplicated of consecutive repeats -- for the summary line. */
	public List<String> lineNamesInOrder() {
		final Set<String> seen = new LinkedHashSet<>();
		for (final RouteLeg leg : legs) {
			if (!leg.isWalk()) {
				seen.add(leg.lineName());
			}
		}
		return seen.stream().collect(Collectors.toList());
	}

	public String summary() {
		return String.join(" + ", lineNamesInOrder());
	}
}
