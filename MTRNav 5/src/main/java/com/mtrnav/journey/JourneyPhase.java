package com.mtrnav.journey;

public enum JourneyPhase {
	/** Walking towards the boarding platform of the current leg. */
	WALKING,
	/** Inside the station/platform boundary, waiting to board. */
	PLATFORM,
	/** Just mounted a vehicle -- confirming it's on the expected line. */
	BOARDING,
	/** Riding, tracking progress through intermediate stops. */
	TRANSIT,
	/** Dismounted at the alight platform of the final leg. */
	ARRIVAL
}
