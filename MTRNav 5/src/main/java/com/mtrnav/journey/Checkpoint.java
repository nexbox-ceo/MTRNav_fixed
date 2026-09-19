package com.mtrnav.journey;

import org.mtr.core.data.Position;

/** The single point the HUD direction pointer currently aims at, plus a label for the distance string. */
public final class Checkpoint {

	public final Position position;
	public final String label;

	public Checkpoint(Position position, String label) {
		this.position = position;
		this.label = label;
	}
}
