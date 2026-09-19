package com.mtrnav.routing;

import org.mtr.core.data.Route;

/**
 * One directed edge in the transit graph, from whichever platform ID owns
 * this edge in {@link TransitGraph}'s adjacency map to {@link #toPlatformId}.
 *
 * {@code route == null} means this is a walking/transfer edge (moving
 * between two platforms of the same station on foot) rather than riding a
 * vehicle.
 */
public final class GraphEdge {

	public final long toPlatformId;
	public final Route route;
	public final long weightMillis;

	public GraphEdge(long toPlatformId, Route route, long weightMillis) {
		this.toPlatformId = toPlatformId;
		this.route = route;
		this.weightMillis = Math.max(1, weightMillis);
	}

	public boolean isWalk() {
		return route == null;
	}
}
