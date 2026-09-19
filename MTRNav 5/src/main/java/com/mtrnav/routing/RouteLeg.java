package com.mtrnav.routing;

import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;

import java.util.List;

/** A single continuous ride on one line, or a walking transfer between platforms. */
public final class RouteLeg {

	public final Route route; // null == walking leg
	public final Platform boardPlatform;
	public final Platform alightPlatform;
	public final List<Platform> intermediateStops;
	public final long durationMillis;

	public RouteLeg(Route route, Platform boardPlatform, Platform alightPlatform, List<Platform> intermediateStops, long durationMillis) {
		this.route = route;
		this.boardPlatform = boardPlatform;
		this.alightPlatform = alightPlatform;
		this.intermediateStops = intermediateStops;
		this.durationMillis = durationMillis;
	}

	public boolean isWalk() {
		return route == null;
	}

	public String lineName() {
		return isWalk() ? "Walk" : route.getName();
	}
}
