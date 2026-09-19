package com.mtrnav.journey;

import com.mtrnav.compat.MTRDataBridge;
import com.mtrnav.client.SoundHelper;
import com.mtrnav.routing.RouteLeg;
import com.mtrnav.routing.RouteOption;
import com.mtrnav.tts.TtsEngine;
import com.mtrnav.util.DistanceUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Station;
import org.mtr.core.operation.ArrivalResponse;

import javax.annotation.Nullable;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Drives the five-phase turn-by-turn state machine, and everything the
 * bottom-left journey widget ({@link com.mtrnav.client.JourneyHudOverlay})
 * shows: the "To:" line, the single current instruction, the live countdown,
 * the fixed departure/arrival clock, and delay detection.
 *
 * HONESTY NOTE on the Boarding phase: confirming that the vehicle a player
 * just mounted is genuinely serving the expected line requires resolving
 * the ridden Minecraft entity back to MTR's internal
 * {@code VehicleExtraData#getThisRouteId()}. That link exists inside MTR's
 * own code (see {@code org.mtr.mod.data.VehicleExtension}) but the exact
 * client-side entity/seat class it's exposed through wasn't something this
 * build could confidently verify. Rather than guess a plausible-looking
 * method that might not exist, {@link #tick} currently treats "the player
 * is riding *something*" at the right platform as boarding success.
 * {@link #confirmBoardedCorrectLine} is the single method to fill in once
 * you've confirmed that link against your exact MTR jar.
 *
 * HONESTY NOTE on delay: {@link #currentDelayMillis()} is sourced from
 * {@code ArrivalResponse#getDeviation()} for the platform being WAITED at
 * (the same live feed the Times app uses), and is assumed positive = late.
 * There's no equivalent live signal for a delay that develops *while
 * already riding* without the same VehicleExtraData link above, so the
 * last value seen before boarding is what the widget keeps showing during
 * TRANSIT -- it won't pick up a delay that starts mid-ride.
 */
public final class JourneyManager {

	private static final double PLATFORM_RADIUS_BLOCKS = 5.0;
	private static final double STOP_PASS_RADIUS_BLOCKS = 8.0;
	private static final double OFF_COURSE_BLOCKS = 40.0;
	private static final long DELAY_THRESHOLD_MILLIS = 30_000L;
	private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	private RouteOption activeRoute;
	private int legIndex;
	private JourneyPhase phase;
	private double closestDistanceThisPhase = Double.MAX_VALUE;

	private long departureRealMillis;
	private String departureClockLabel = "";
	private String arrivalClockLabel = "";

	private List<Platform> legStopSequence = List.of();
	private int nextStopIndex;

	@Nullable
	private ArrivalResponse currentWaitArrival;
	private int waitRefreshCooldown;

	// --- Voice announcement bookkeeping (see TtsEngine) -----------------------
	@Nullable
	private String lastAnnouncedWalkInstruction;
	private boolean announcedWaitForThisPlatform;
	private boolean announcedFinalStopWarning;

	@Nullable
	private JourneyListener listener;

	public interface JourneyListener {
		void onPhaseChanged(JourneyPhase phase, RouteLeg currentLeg);

		void onRecenter();

		void onArrived();
	}

	public void setListener(@Nullable JourneyListener listener) {
		this.listener = listener;
	}

	public boolean isActive() {
		return activeRoute != null;
	}

	public void start(RouteOption route) {
		this.activeRoute = route;
		this.legIndex = 0;
		this.departureRealMillis = System.currentTimeMillis();
		final LocalTime departure = LocalTime.now();
		final LocalTime arrival = departure.plusSeconds(route.totalDurationMillis / 1000);
		this.departureClockLabel = departure.format(CLOCK_FORMAT);
		this.arrivalClockLabel = arrival.format(CLOCK_FORMAT);
		enterPhase(JourneyPhase.WALKING);
		TtsEngine.speak("Let's go to " + MTRDataBridge.stationName(route.destination));
		lastAnnouncedWalkInstruction = walkingInstruction(currentLeg());
		TtsEngine.speak(lastAnnouncedWalkInstruction);
	}

	public void stop() {
		activeRoute = null;
		legIndex = 0;
		phase = null;
		currentWaitArrival = null;
		legStopSequence = List.of();
		JourneyPersistence.clear();
	}

	/**
	 * Restores a journey reconstructed from a save file (see
	 * {@link JourneyPersistence}) after a relog -- unlike {@link #start},
	 * this preserves the original departure/arrival clock instead of
	 * resetting it, and doesn't replay the "Let's go to..." announcement.
	 */
	public void resume(RouteOption route, int legIndex, JourneyPhase phase, long departureRealMillis, String departureClockLabel, String arrivalClockLabel, int nextStopIndex) {
		this.activeRoute = route;
		this.legIndex = Math.max(0, Math.min(legIndex, route.legs.size() - 1));
		this.phase = phase;
		this.departureRealMillis = departureRealMillis;
		this.departureClockLabel = departureClockLabel;
		this.arrivalClockLabel = arrivalClockLabel;
		this.closestDistanceThisPhase = Double.MAX_VALUE;
		this.currentWaitArrival = null;
		// Suppress replaying voice lines for whatever we're resuming into.
		lastAnnouncedWalkInstruction = phase == JourneyPhase.WALKING ? walkingInstruction(currentLeg()) : null;
		announcedWaitForThisPlatform = true;
		announcedFinalStopWarning = true;
		if (phase == JourneyPhase.BOARDING || phase == JourneyPhase.TRANSIT) {
			final RouteLeg leg = currentLeg();
			final List<Platform> stops = new ArrayList<>();
			if (leg != null) {
				stops.addAll(leg.intermediateStops);
				stops.add(leg.alightPlatform);
			}
			legStopSequence = stops;
			this.nextStopIndex = Math.max(0, Math.min(nextStopIndex, stops.size() - 1));
		}
		TtsEngine.speak("Journey resumed");
		JourneyPersistence.save(this);
	}

	public RouteOption getActiveRoute() {
		return activeRoute;
	}

	public int getLegIndex() {
		return legIndex;
	}

	public long getDepartureRealMillis() {
		return departureRealMillis;
	}

	public String getDepartureClockLabel() {
		return departureClockLabel;
	}

	public String getArrivalClockLabel() {
		return arrivalClockLabel;
	}

	public int getNextStopIndex() {
		return nextStopIndex;
	}

	@Nullable
	public JourneyPhase getPhase() {
		return phase;
	}

	@Nullable
	public RouteLeg currentLeg() {
		if (activeRoute == null || legIndex >= activeRoute.legs.size()) {
			return null;
		}
		return activeRoute.legs.get(legIndex);
	}

	@Nullable
	public Checkpoint currentCheckpoint() {
		final RouteLeg leg = currentLeg();
		if (leg == null) {
			return null;
		}
		if (phase == JourneyPhase.TRANSIT || phase == JourneyPhase.BOARDING) {
			final Platform target = nextStopPlatform() != null ? nextStopPlatform() : leg.alightPlatform;
			return new Checkpoint(target.getMidPosition(), target.getStationName());
		}
		return new Checkpoint(leg.boardPlatform.getMidPosition(), leg.boardPlatform.getStationName());
	}

	/** The widget's "To:" line -- the final destination normally, or the next upcoming stop while riding. */
	public String toLabel() {
		if (activeRoute == null) {
			return "";
		}
		if (phase == JourneyPhase.TRANSIT || phase == JourneyPhase.BOARDING) {
			final Platform nextStop = nextStopPlatform();
			if (nextStop != null) {
				return nextStop.getStationName();
			}
		}
		return MTRDataBridge.stationName(activeRoute.destination);
	}

	/** The single current turn-by-turn instruction shown in the middle of the widget. */
	public String instructionText() {
		final RouteLeg leg = currentLeg();
		if (leg == null || phase == null) {
			return "";
		}
		return switch (phase) {
			case WALKING -> walkingInstruction(leg);
			case PLATFORM -> leg.isWalk() ? "Walk to " + leg.alightPlatform.getName() : waitingInstruction(leg);
			case BOARDING, TRANSIT -> "Drop off at " + leg.alightPlatform.getStationName();
			case ARRIVAL -> "Arrived";
		};
	}

	private String walkingInstruction(RouteLeg leg) {
		final String verb = legIndex == 0 ? "Go to" : "Walk to";
		final Station boardStation = leg.boardPlatform.area;
		final boolean multiPlatform = boardStation != null && boardStation.savedRails.size() > 1;
		if (multiPlatform && !isPlayerInArea(boardStation)) {
			return verb + " " + MTRDataBridge.stationName(boardStation);
		}
		return verb + " " + leg.boardPlatform.getName();
	}

	private String waitingInstruction(RouteLeg leg) {
		if (currentWaitArrival != null && currentWaitArrival.getRouteId() == leg.route.getId()) {
			final long minutes = Math.max(0, MTRDataBridge.etaMillis(currentWaitArrival) / 60_000);
			return "Wait " + minutes + " minute" + (minutes == 1 ? "" : "s") + " for the " + leg.lineName();
		}
		return "Waiting for the " + leg.lineName();
	}

	private boolean isPlayerInArea(Station station) {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return false;
		}
		final BlockPos pos = client.player.getBlockPos();
		return station.inArea(new Position(pos.getX(), pos.getY(), pos.getZ()));
	}

	/**
	 * Minutes left on the whole journey, recomputed live from real elapsed
	 * time every call -- this ticks down continuously, unlike
	 * {@link #scheduleClockRange()} which is fixed once at {@link #start}.
	 */
	public long remainingMinutes() {
		if (activeRoute == null) {
			return 0;
		}
		final long elapsed = System.currentTimeMillis() - departureRealMillis;
		final long remainingMillis = Math.max(0, activeRoute.totalDurationMillis - elapsed);
		return (remainingMillis + 59_999) / 60_000; // ceiling: avoids showing "0 min" a minute early
	}

	public String scheduleClockRange() {
		return departureClockLabel + " - " + arrivalClockLabel;
	}

	public long currentDelayMillis() {
		return currentWaitArrival != null ? currentWaitArrival.getDeviation() : 0;
	}

	public boolean isDelayed() {
		return Math.abs(currentDelayMillis()) >= DELAY_THRESHOLD_MILLIS;
	}

	@Nullable
	private Platform nextStopPlatform() {
		if (nextStopIndex < 0 || nextStopIndex >= legStopSequence.size()) {
			return null;
		}
		return legStopSequence.get(nextStopIndex);
	}

	/** Called every client tick; safe to call even when {@link #isActive()} is false. */
	public void tick() {
		if (!isActive()) {
			return;
		}
		final MinecraftClient client = MinecraftClient.getInstance();
		final ClientPlayerEntity player = client.player;
		if (player == null) {
			return;
		}
		final RouteLeg leg = currentLeg();
		if (leg == null) {
			enterPhase(JourneyPhase.ARRIVAL);
			if (listener != null) {
				listener.onArrived();
			}
			stop();
			return;
		}

		final BlockPos playerPos = player.getBlockPos();
		final Checkpoint checkpoint = currentCheckpoint();
		final double distance = checkpoint == null ? 0 : DistanceUtil.distance(playerPos, checkpoint.position);

		switch (phase) {
			case WALKING -> {
				trackOffCourse(distance);
				final String instr = walkingInstruction(leg);
				if (!instr.equals(lastAnnouncedWalkInstruction)) {
					TtsEngine.speak(instr);
					lastAnnouncedWalkInstruction = instr;
				}
				if (distance <= PLATFORM_RADIUS_BLOCKS) {
					enterPhase(JourneyPhase.PLATFORM);
				}
			}
			case PLATFORM -> {
				if (leg.isWalk()) {
					advanceLeg();
				} else {
					refreshWaitArrival(leg);
					if (!announcedWaitForThisPlatform && currentWaitArrival != null) {
						final long minutes = Math.max(0, MTRDataBridge.etaMillis(currentWaitArrival) / 60_000);
						final String destination = currentWaitArrival.getDestination();
						TtsEngine.speak("Wait " + minutes + " minute" + (minutes == 1 ? "" : "s") + " for the " + leg.lineName()
								+ (destination == null || destination.isEmpty() ? "" : " to " + destination));
						announcedWaitForThisPlatform = true;
					}
					if (player.hasVehicle()) {
						enterPhase(JourneyPhase.BOARDING);
					}
				}
			}
			case BOARDING -> {
				if (!player.hasVehicle()) {
					enterPhase(JourneyPhase.PLATFORM);
				} else if (confirmBoardedCorrectLine(player, leg)) {
					enterPhase(JourneyPhase.TRANSIT);
				}
			}
			case TRANSIT -> {
				trackOffCourse(distance);
				advanceNextStopIfPassed(playerPos);
				if (!announcedFinalStopWarning && nextStopIndex == legStopSequence.size() - 1) {
					TtsEngine.speak("Drop off at " + leg.alightPlatform.getStationName());
					announcedFinalStopWarning = true;
				}
				if (!player.hasVehicle() && distance <= PLATFORM_RADIUS_BLOCKS) {
					advanceLeg();
				}
			}
			case ARRIVAL -> {
				if (listener != null) {
					listener.onArrived();
				}
				stop();
			}
		}
	}

	private void advanceNextStopIfPassed(BlockPos playerPos) {
		final Platform next = nextStopPlatform();
		if (next == null) {
			return;
		}
		if (DistanceUtil.distance(playerPos, next.getMidPosition()) <= STOP_PASS_RADIUS_BLOCKS && nextStopIndex < legStopSequence.size() - 1) {
			nextStopIndex++;
		}
	}

	private void refreshWaitArrival(RouteLeg leg) {
		if (waitRefreshCooldown-- > 0) {
			return;
		}
		waitRefreshCooldown = 10;
		final List<ArrivalResponse> arrivals = MTRDataBridge.requestArrivals(List.of(leg.boardPlatform.getId()));
		currentWaitArrival = arrivals.stream()
				.filter(a -> a.getRouteId() == leg.route.getId())
				.min(Comparator.comparingLong(MTRDataBridge::etaMillis))
				.orElse(null);
	}

	private void advanceLeg() {
		legIndex++;
		currentWaitArrival = null;
		if (legIndex >= activeRoute.legs.size()) {
			enterPhase(JourneyPhase.ARRIVAL);
		} else {
			enterPhase(JourneyPhase.WALKING);
		}
	}

	private void enterPhase(JourneyPhase newPhase) {
		this.phase = newPhase;
		this.closestDistanceThisPhase = Double.MAX_VALUE;
		if (newPhase == JourneyPhase.WALKING) {
			lastAnnouncedWalkInstruction = null;
		}
		if (newPhase == JourneyPhase.PLATFORM) {
			announcedWaitForThisPlatform = false;
		}
		if (newPhase == JourneyPhase.BOARDING) {
			announcedFinalStopWarning = false;
			final RouteLeg leg = currentLeg();
			final List<Platform> stops = new ArrayList<>();
			if (leg != null) {
				stops.addAll(leg.intermediateStops);
				stops.add(leg.alightPlatform);
			}
			legStopSequence = stops;
			nextStopIndex = 0;
		}
		if (newPhase == JourneyPhase.ARRIVAL) {
			SoundHelper.playArrivalPing();
		}
		if (listener != null) {
			listener.onPhaseChanged(newPhase, currentLeg());
		}
		if (activeRoute != null) {
			JourneyPersistence.save(this);
		}
	}

	private void trackOffCourse(double currentDistance) {
		if (currentDistance < closestDistanceThisPhase) {
			closestDistanceThisPhase = currentDistance;
		} else if (currentDistance - closestDistanceThisPhase > OFF_COURSE_BLOCKS) {
			recenter();
		}
	}

	/** Re-runs the journey search from the player's nearest station to the original destination. */
	private void recenter() {
		if (listener != null) {
			listener.onRecenter();
		}
		// The caller (MTRNavApp) is expected to re-issue RouteFinder.findRoutes
		// from the player's current nearest station and call start() again with
		// whichever result it picks -- kept out of this class so JourneyManager
		// doesn't need to own UI/selection concerns. See MTRNavApp#onRecenter.
		closestDistanceThisPhase = Double.MAX_VALUE;
	}

	/**
	 * See class-level HONESTY NOTE. Default: assume success. Replace the body
	 * once you've confirmed how to read {@code VehicleExtraData#getThisRouteId()}
	 * for the entity the player is currently riding on your exact MTR build.
	 */
	private boolean confirmBoardedCorrectLine(ClientPlayerEntity player, RouteLeg leg) {
		return true;
	}
}
