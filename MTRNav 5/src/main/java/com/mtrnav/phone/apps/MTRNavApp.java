package com.mtrnav.phone.apps;

import com.mtrnav.MTRNavClient;
import com.mtrnav.compat.MTRDataBridge;
import com.mtrnav.journey.JourneyManager;
import com.mtrnav.journey.JourneyPhase;
import com.mtrnav.phone.GuiDraw;
import com.mtrnav.phone.MiniMapWidget;
import com.mtrnav.phone.PhoneApp;
import com.mtrnav.phone.PhoneScreen;
import com.mtrnav.routing.RouteFinder;
import com.mtrnav.routing.RouteLeg;
import com.mtrnav.routing.RouteOption;
import com.mtrnav.report.FeedbackStore;
import com.mtrnav.report.TripReportWriter;
import com.mtrnav.report.TripSummary;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.Route;
import org.mtr.core.data.Station;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The journey-planning app. Views roughly mirror the phone mockup:
 * NEARBY (default) -> PLANNER -> (PICK_ORIGIN/PICK_DESTINATION/PICK_AVOID) ->
 * SEARCHING -> RESULTS -> ACTIVE_JOURNEY.
 */
public final class MTRNavApp implements PhoneApp, JourneyManager.JourneyListener {

	private enum View {NEARBY, PLANNER, PICK_ORIGIN, PICK_DESTINATION, PICK_AVOID, SEARCHING, RESULTS, ACTIVE_JOURNEY, TRIP_COMPLETE}

	private static final int ROW_HEIGHT = 20;

	private View view = View.NEARBY;

	@Nullable
	private Station origin, destination;
	@Nullable
	private Route avoidLine;

	private List<RouteOption> results = List.of();
	@Nullable
	private CompletableFuture<List<RouteOption>> pendingSearch;
	private int searchDots;

	private List<Station> nearbyStations = List.of();
	private int nearbyRefreshCooldown;
	private int scrollOffset;

	private final MiniMapWidget miniMap = new MiniMapWidget();

	@Nullable
	private TripSummary lastCompletedTrip;
	@Nullable
	private String lastSavedReportMessage;
	private int currentTripRating;

	@Override
	public String title() {
		return "MTRNav";
	}

	@Nullable
	private PlannerPrefs.Loaded previousTrip;

	@Override
	public void onOpen(PhoneScreen phone) {
		MTRNavClient.JOURNEY.setListener(this);
		if (MTRNavClient.JOURNEY.isActive()) {
			view = View.ACTIVE_JOURNEY;
		} else if (MTRDataBridge.isDataAvailable()) {
			previousTrip = PlannerPrefs.load();
			if (origin == null && destination == null && avoidLine == null) {
				origin = previousTrip.origin;
				destination = previousTrip.destination;
				avoidLine = previousTrip.avoidLine;
			}
		}
	}

	@Override
	public void tick(PhoneScreen phone) {
		if (view == View.NEARBY && nearbyRefreshCooldown-- <= 0) {
			nearbyRefreshCooldown = 10;
			refreshNearbyStations();
		}
		if (view == View.SEARCHING) {
			searchDots++;
			if (pendingSearch != null && pendingSearch.isDone()) {
				results = pendingSearch.getNow(List.of());
				pendingSearch = null;
				scrollOffset = 0;
				view = View.RESULTS;
			}
		}
	}

	private void refreshNearbyStations() {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || !MTRDataBridge.isDataAvailable()) {
			nearbyStations = List.of();
			return;
		}
		final BlockPos playerPos = client.player.getBlockPos();
		final List<Station> stations = new ArrayList<>(MTRDataBridge.getStations());
		stations.sort(Comparator.comparingDouble(s -> MTRDataBridge.closestPlatformDistance(playerPos, s)));
		nearbyStations = stations.size() > 20 ? stations.subList(0, 20) : stations;
	}

	// --- Rendering -----------------------------------------------------------

	@Override
	public void render(PhoneScreen phone, MatrixStack matrices, int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks) {
		switch (view) {
			case NEARBY -> renderNearby(matrices, x, y, width, height, mouseX, mouseY);
			case PLANNER -> renderPlanner(matrices, x, y, width, height, mouseX, mouseY);
			case PICK_ORIGIN -> renderStationPicker(matrices, x, y, width, height, mouseX, mouseY);
			case PICK_DESTINATION -> renderStationPicker(matrices, x, y, width, height, mouseX, mouseY);
			case PICK_AVOID -> renderLinePicker(matrices, x, y, width, height, mouseX, mouseY);
			case SEARCHING -> renderSearching(matrices, x, y, width, height);
			case RESULTS -> renderResults(matrices, x, y, width, height, mouseX, mouseY);
			case ACTIVE_JOURNEY -> renderActiveJourney(matrices, x, y, width, height);
			case TRIP_COMPLETE -> renderTripComplete(matrices, x, y, width, height, mouseX, mouseY);
		}
	}

	private void renderNearby(MatrixStack matrices, int x, int y, int width, int height, int mouseX, int mouseY) {
		final MinecraftClient client = MinecraftClient.getInstance();
		final boolean hovered = mouseY >= y + 6 && mouseY <= y + 6 + 16 && mouseX >= x + 8 && mouseX <= x + width - 8;
		GuiDraw.fill(matrices, x + 8, y + 6, x + width - 8, y + 22, hovered ? 0xFF2E2E3A : 0xFF1C1C22);
		GuiDraw.drawCenteredText(matrices, client.textRenderer, "SEARCH ROUTE HERE", x + width / 2, y + 11, 0xFFFFFF);

		int listStartY = y + 42;
		if (hasPreviousTrip()) {
			final int cardY = y + 26;
			final boolean cardHovered = mouseY >= cardY && mouseY <= cardY + 28 && mouseX >= x + 8 && mouseX <= x + width - 8;
			GuiDraw.fill(matrices, x + 8, cardY, x + width - 8, cardY + 28, cardHovered ? 0xFF283A28 : 0xFF1A2A1A);
			GuiDraw.drawTextWithShadow(matrices, client.textRenderer, "Previous journey \u2192 tap to search again", x + 12, cardY + 3, 0x88CC88);
			final String route = MTRDataBridge.stationName(previousTrip.origin) + " \u2192 " + MTRDataBridge.stationName(previousTrip.destination);
			GuiDraw.drawTextWithShadow(matrices, client.textRenderer, route, x + 12, cardY + 15, 0xFFFFFF);
			listStartY = cardY + 34;
		}

		GuiDraw.drawTextWithShadow(matrices, client.textRenderer, "Nearest station(s):", x + 8, listStartY - 12, 0x888888);
		if (!MTRDataBridge.isDataAvailable()) {
			GuiDraw.drawTextWithShadow(matrices, client.textRenderer, "No railway data on this world", x + 8, listStartY, 0x888888);
			return;
		}
		final BlockPos playerPos = client.player == null ? BlockPos.ORIGIN : client.player.getBlockPos();
		int rowY = listStartY;
		for (final Station station : nearbyStations) {
			if (rowY > y + height - 10) {
				break;
			}
			final double d = MTRDataBridge.closestPlatformDistance(playerPos, station);
			GuiDraw.drawTextWithShadow(matrices, client.textRenderer, MTRDataBridge.stationName(station) + " (" + Math.round(d) + "m away)", x + 8, rowY, 0xCCCCCC);
			rowY += 11;
		}
	}

	private boolean hasPreviousTrip() {
		return previousTrip != null && previousTrip.origin != null && previousTrip.destination != null;
	}

	private void renderPlanner(MatrixStack matrices, int x, int y, int width, int height, int mouseX, int mouseY) {
		final MinecraftClient client = MinecraftClient.getInstance();
		int rowY = y + 8;
		rowY = renderField(matrices, x, rowY, width, mouseX, mouseY, "Origin", origin == null ? null : MTRDataBridge.stationName(origin));
		rowY = renderField(matrices, x, rowY, width, mouseX, mouseY, "Destination", destination == null ? null : MTRDataBridge.stationName(destination));
		rowY = renderField(matrices, x, rowY, width, mouseX, mouseY, "Avoid", avoidLine == null ? "-" : MTRDataBridge.routeName(avoidLine));

		final boolean canSearch = origin != null && destination != null && origin != destination;
		final boolean hovered = canSearch && mouseY >= rowY + 8 && mouseY <= rowY + 24 && mouseX >= x + 8 && mouseX <= x + width - 8;
		GuiDraw.fill(matrices, x + 8, rowY + 8, x + width - 8, rowY + 24, !canSearch ? 0xFF1A1A1E : hovered ? 0xFF2E4A2E : 0xFF1E3A1E);
		GuiDraw.drawCenteredText(matrices, client.textRenderer, "CHECK FOR ROUTES", x + width / 2, rowY + 13, canSearch ? 0xFFFFFF : 0x666666);
	}

	private int renderField(MatrixStack matrices, int x, int y, int width, int mouseX, int mouseY, String label, @Nullable String value) {
		final MinecraftClient client = MinecraftClient.getInstance();
		final boolean hovered = mouseY >= y && mouseY <= y + 16 && mouseX >= x + 8 && mouseX <= x + width - 8;
		GuiDraw.fill(matrices, x + 8, y, x + width - 8, y + 16, hovered ? 0xFF2A2A34 : 0xFF1C1C22);
		GuiDraw.drawTextWithShadow(matrices, client.textRenderer, label + ": " + (value == null ? "(tap to choose)" : value), x + 12, y + 4, value == null ? 0x777777 : 0xFFFFFF);
		return y + 16 + 6;
	}

	private void renderStationPicker(MatrixStack matrices, int x, int y, int width, int height, int mouseX, int mouseY) {
		final List<Station> options = new ArrayList<>(MTRDataBridge.getStations());
		options.sort(Comparator.comparing(MTRDataBridge::stationName));
		renderRows(matrices, x, y, width, height, mouseX, mouseY, options.stream().map(MTRDataBridge::stationName).toList());
	}

	private void renderLinePicker(MatrixStack matrices, int x, int y, int width, int height, int mouseX, int mouseY) {
		final List<Route> options = new ArrayList<>(MTRDataBridge.getRoutes());
		options.sort(Comparator.comparing(MTRDataBridge::routeName));
		final List<String> labels = new ArrayList<>();
		labels.add("(none)");
		options.forEach(r -> labels.add(MTRDataBridge.routeName(r)));
		renderRows(matrices, x, y, width, height, mouseX, mouseY, labels);
	}

	private void renderRows(MatrixStack matrices, int x, int y, int width, int height, int mouseX, int mouseY, List<String> labels) {
		final MinecraftClient client = MinecraftClient.getInstance();
		int rowY = y + 4 - scrollOffset;
		for (final String label : labels) {
			if (rowY + ROW_HEIGHT >= y && rowY <= y + height) {
				final boolean hovered = mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 2 && mouseX >= x + 6 && mouseX <= x + width - 6;
				GuiDraw.fill(matrices, x + 6, rowY, x + width - 6, rowY + ROW_HEIGHT - 2, hovered ? 0xFF26262E : 0xFF1A1A1F);
				GuiDraw.drawTextWithShadow(matrices, client.textRenderer, label, x + 10, rowY + 5, 0xDDDDDD);
			}
			rowY += ROW_HEIGHT;
		}
	}

	private void renderSearching(MatrixStack matrices, int x, int y, int width, int height) {
		final MinecraftClient client = MinecraftClient.getInstance();
		final String dots = ".".repeat((searchDots / 10) % 4);
		GuiDraw.drawCenteredText(matrices, client.textRenderer, "Finding routes" + dots, x + width / 2, y + height / 2 - 4, 0xCCCCCC);
	}

	private void renderResults(MatrixStack matrices, int x, int y, int width, int height, int mouseX, int mouseY) {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (results.isEmpty()) {
			GuiDraw.drawCenteredText(matrices, client.textRenderer, "No route found", x + width / 2, y + height / 2 - 4, 0x888888);
			return;
		}
		int rowY = y + 6;
		for (int i = 0; i < results.size(); i++) {
			final RouteOption option = results.get(i);
			final int cardHeight = 40;
			final boolean hovered = mouseY >= rowY && mouseY <= rowY + cardHeight && mouseX >= x + 6 && mouseX <= x + width - 6;
			GuiDraw.fill(matrices, x + 6, rowY, x + width - 6, rowY + cardHeight, hovered ? 0xFF23232B : 0xFF191920);
			GuiDraw.drawTextWithShadow(matrices, client.textRenderer, "ROUTE " + (i + 1) + ":", x + 10, rowY + 3, 0xFFFFFF);
			GuiDraw.drawTextWithShadow(matrices, client.textRenderer, option.totalMinutes() + " minutes", x + 10, rowY + 14, 0x55FF55);
			GuiDraw.drawTextWithShadow(matrices, client.textRenderer, option.summary(), x + 10, rowY + 25, 0xAAAAAA);
			rowY += cardHeight + 6;
		}
		GuiDraw.drawCenteredText(matrices, client.textRenderer, "Click a route to start", x + width / 2, y + height - 10, 0x777777);
	}

	private void renderActiveJourney(MatrixStack matrices, int x, int y, int width, int height) {
		final MinecraftClient client = MinecraftClient.getInstance();
		final JourneyManager journey = MTRNavClient.JOURNEY;
		if (!journey.isActive()) {
			GuiDraw.drawCenteredText(matrices, client.textRenderer, "Journey complete", x + width / 2, y + height / 2 - 4, 0xCCCCCC);
			return;
		}
		// Mirrors the bottom-left HUD widget (see JourneyHudOverlay) so the
		// in-phone view and the closed-phone HUD always agree.
		GuiDraw.drawCenteredText(matrices, client.textRenderer, "To: " + journey.toLabel(), x + width / 2, y + 4, 0xFFFFFF);
		GuiDraw.drawCenteredText(matrices, client.textRenderer, journey.instructionText(), x + width / 2, y + 16, 0xCCFFCC);
		GuiDraw.drawCenteredText(matrices, client.textRenderer, journey.remainingMinutes() + " min | " + journey.scheduleClockRange(), x + width / 2, y + 27, 0x999999);
		miniMap.render(matrices, x + 8, y + 40, width - 16, height - 48);
	}

	private void renderTripComplete(MatrixStack matrices, int x, int y, int width, int height, int mouseX, int mouseY) {
		final MinecraftClient client = MinecraftClient.getInstance();
		final int centerX = x + width / 2;
		if (lastCompletedTrip == null) {
			GuiDraw.drawCenteredText(matrices, client.textRenderer, "Journey complete", centerX, y + height / 2 - 4, 0xCCCCCC);
			return;
		}
		final TripSummary trip = lastCompletedTrip;
		GuiDraw.drawCenteredText(matrices, client.textRenderer, "You've arrived!", centerX, y + 8, 0x55FF55);
		GuiDraw.drawCenteredText(matrices, client.textRenderer, trip.originName + " \u2192 " + trip.destinationName, centerX, y + 20, 0xFFFFFF);
		GuiDraw.drawCenteredText(matrices, client.textRenderer, trip.lineSummary.isEmpty() ? "(walked)" : trip.lineSummary, centerX, y + 31, 0xAAAAAA);
		GuiDraw.drawCenteredText(matrices, client.textRenderer, trip.actualMinutes + " min (planned " + trip.plannedMinutes + ")", centerX, y + 44, 0x999999);

		final int ratingY = y + 58;
		GuiDraw.drawCenteredText(matrices, client.textRenderer, currentTripRating > 0 ? "Thanks for rating this trip!" : "Rate this trip:", centerX, ratingY, 0x888888);
		final int starsWidth = 5 * 18;
		final int starsX = centerX - starsWidth / 2;
		for (int i = 1; i <= 5; i++) {
			final int boxX = starsX + (i - 1) * 18;
			final int boxY = ratingY + 10;
			final boolean filled = i <= currentTripRating;
			final boolean hovered = currentTripRating == 0 && mouseX >= boxX && mouseX <= boxX + 16 && mouseY >= boxY && mouseY <= boxY + 14;
			GuiDraw.fill(matrices, boxX, boxY, boxX + 16, boxY + 14, filled ? 0xFF3A6A2E : hovered ? 0xFF2E2E3A : 0xFF1A1A1F);
			GuiDraw.drawCenteredText(matrices, client.textRenderer, String.valueOf(i), boxX + 8, boxY + 3, filled ? 0xFFFFFF : 0x999999);
		}

		final int buttonY = ratingY + 30;
		final boolean hovered = mouseY >= buttonY && mouseY <= buttonY + 18 && mouseX >= x + 10 && mouseX <= x + width - 10;
		GuiDraw.fill(matrices, x + 10, buttonY, x + width - 10, buttonY + 18, hovered ? 0xFF2E4A2E : 0xFF1E3A1E);
		GuiDraw.drawCenteredText(matrices, client.textRenderer, "Save Trip Report", centerX, buttonY + 5, 0xFFFFFF);

		if (lastSavedReportMessage != null) {
			GuiDraw.drawCenteredText(matrices, client.textRenderer, lastSavedReportMessage, centerX, buttonY + 26, 0x88CC88);
		}

		FeedbackStore.getAverage().ifPresent(average ->
				GuiDraw.drawCenteredText(matrices, client.textRenderer,
						String.format(java.util.Locale.ROOT, "Average rating: %.1f / 5 (%d trips)", average, FeedbackStore.getCount()),
						centerX, y + height - 22, 0x777777));
		GuiDraw.drawCenteredText(matrices, client.textRenderer, "Back to plan another trip", centerX, y + height - 10, 0x777777);
	}

	// --- Input -----------------------------------------------------------

	@Override
	public boolean mouseClicked(PhoneScreen phone, int x, int y, int width, int height, double mouseX, double mouseY, int button) {
		return switch (view) {
			case NEARBY -> {
				if (mouseY >= y + 6 && mouseY <= y + 22 && mouseX >= x + 8 && mouseX <= x + width - 8) {
					view = View.PLANNER;
					yield true;
				}
				if (hasPreviousTrip()) {
					final int cardY = y + 26;
					if (mouseY >= cardY && mouseY <= cardY + 28 && mouseX >= x + 8 && mouseX <= x + width - 8) {
						origin = previousTrip.origin;
						destination = previousTrip.destination;
						avoidLine = previousTrip.avoidLine;
						startSearch();
						yield true;
					}
				}
				yield false;
			}
			case PLANNER -> clickPlanner(x, y, width, mouseX, mouseY);
			case PICK_ORIGIN -> clickStationPicker(mouseY, y, height, true);
			case PICK_DESTINATION -> clickStationPicker(mouseY, y, height, false);
			case PICK_AVOID -> clickLinePicker(mouseY, y, height);
			case RESULTS -> clickResults(x, y, width, mouseX, mouseY);
			case TRIP_COMPLETE -> clickTripComplete(x, y, width, mouseX, mouseY);
			default -> false;
		};
	}

	private boolean clickTripComplete(int x, int y, int width, double mouseX, double mouseY) {
		final int ratingY = y + 58;
		if (currentTripRating == 0) {
			final int starsWidth = 5 * 18;
			final int starsX = x + width / 2 - starsWidth / 2;
			for (int i = 1; i <= 5; i++) {
				final int boxX = starsX + (i - 1) * 18;
				final int boxY = ratingY + 10;
				if (mouseX >= boxX && mouseX <= boxX + 16 && mouseY >= boxY && mouseY <= boxY + 14) {
					currentTripRating = i;
					FeedbackStore.recordRating(i);
					return true;
				}
			}
		}
		final int buttonY = ratingY + 30;
		if (mouseY >= buttonY && mouseY <= buttonY + 18 && mouseX >= x + 10 && mouseX <= x + width - 10 && lastCompletedTrip != null) {
			final var saved = TripReportWriter.save(lastCompletedTrip);
			lastSavedReportMessage = saved.isPresent() ? "Saved: " + saved.get().getFileName() : "Couldn't save (see logs)";
			return true;
		}
		return false;
	}

	private boolean clickPlanner(int x, int y, int width, double mouseX, double mouseY) {
		int rowY = y + 8;
		if (mouseY >= rowY && mouseY <= rowY + 16 && mouseX >= x + 8 && mouseX <= x + width - 8) {
			scrollOffset = 0;
			view = View.PICK_ORIGIN;
			return true;
		}
		rowY += 22;
		if (mouseY >= rowY && mouseY <= rowY + 16 && mouseX >= x + 8 && mouseX <= x + width - 8) {
			scrollOffset = 0;
			view = View.PICK_DESTINATION;
			return true;
		}
		rowY += 22;
		if (mouseY >= rowY && mouseY <= rowY + 16 && mouseX >= x + 8 && mouseX <= x + width - 8) {
			scrollOffset = 0;
			view = View.PICK_AVOID;
			return true;
		}
		rowY += 22;
		final boolean canSearch = origin != null && destination != null && origin != destination;
		if (canSearch && mouseY >= rowY + 8 && mouseY <= rowY + 24 && mouseX >= x + 8 && mouseX <= x + width - 8) {
			startSearch();
			return true;
		}
		return false;
	}

	private boolean clickStationPicker(double mouseY, int y, int height, boolean pickingOrigin) {
		final List<Station> options = new ArrayList<>(MTRDataBridge.getStations());
		options.sort(Comparator.comparing(MTRDataBridge::stationName));
		final int index = rowIndexAt(mouseY, y, height, options.size());
		if (index < 0) {
			return false;
		}
		if (pickingOrigin) {
			origin = options.get(index);
		} else {
			destination = options.get(index);
		}
		PlannerPrefs.save(origin, destination, avoidLine);
		view = View.PLANNER;
		return true;
	}

	private boolean clickLinePicker(double mouseY, int y, int height) {
		final List<Route> options = new ArrayList<>(MTRDataBridge.getRoutes());
		options.sort(Comparator.comparing(MTRDataBridge::routeName));
		final int index = rowIndexAt(mouseY, y, height, options.size() + 1);
		if (index < 0) {
			return false;
		}
		avoidLine = index == 0 ? null : options.get(index - 1);
		PlannerPrefs.save(origin, destination, avoidLine);
		view = View.PLANNER;
		return true;
	}

	private int rowIndexAt(double mouseY, int y, int height, int optionCount) {
		final int relativeY = (int) mouseY - (y + 4) + scrollOffset;
		if (relativeY < 0) {
			return -1;
		}
		final int index = relativeY / ROW_HEIGHT;
		return index >= 0 && index < optionCount ? index : -1;
	}

	private boolean clickResults(int x, int y, int width, double mouseX, double mouseY) {
		int rowY = y + 6;
		final int cardHeight = 40;
		for (final RouteOption option : results) {
			if (mouseY >= rowY && mouseY <= rowY + cardHeight && mouseX >= x + 6 && mouseX <= x + width - 6) {
				MTRNavClient.JOURNEY.start(option);
				view = View.ACTIVE_JOURNEY;
				return true;
			}
			rowY += cardHeight + 6;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(PhoneScreen phone, int x, int y, int width, int height, double mouseX, double mouseY, double amount) {
		if (view == View.PICK_ORIGIN || view == View.PICK_DESTINATION || view == View.PICK_AVOID) {
			scrollOffset = Math.max(0, scrollOffset - (int) (amount * ROW_HEIGHT));
			return true;
		}
		return false;
	}

	@Override
	public boolean onBack(PhoneScreen phone) {
		return switch (view) {
			case PLANNER -> {
				view = View.NEARBY;
				yield true;
			}
			case PICK_ORIGIN, PICK_DESTINATION, PICK_AVOID -> {
				view = View.PLANNER;
				yield true;
			}
			case RESULTS -> {
				view = View.PLANNER;
				yield true;
			}
			case ACTIVE_JOURNEY -> {
				MTRNavClient.JOURNEY.stop();
				view = View.PLANNER;
				yield true;
			}
			case TRIP_COMPLETE -> {
				view = View.NEARBY;
				yield true;
			}
			default -> false;
		};
	}

	private void startSearch() {
		if (origin == null || destination == null) {
			return;
		}
		searchDots = 0;
		view = View.SEARCHING;
		pendingSearch = RouteFinder.findRoutes(origin, destination, avoidLine);
	}

	// --- JourneyManager.JourneyListener --------------------------------------

	@Override
	public void onPhaseChanged(JourneyPhase phase, RouteLeg currentLeg) {
	}

	@Override
	public void onRecenter() {
		if (destination == null) {
			return;
		}
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || !MTRDataBridge.isDataAvailable()) {
			return;
		}
		final BlockPos playerPos = client.player.getBlockPos();
		Station nearestNow = null;
		double best = Double.MAX_VALUE;
		for (final Station station : MTRDataBridge.getStations()) {
			final double d = MTRDataBridge.closestPlatformDistance(playerPos, station);
			if (d < best) {
				best = d;
				nearestNow = station;
			}
		}
		if (nearestNow == null) {
			return;
		}
		final Station recenterFrom = nearestNow;
		RouteFinder.findRoutes(recenterFrom, destination, avoidLine).thenAccept(options -> {
			if (!options.isEmpty()) {
				client.execute(() -> MTRNavClient.JOURNEY.start(options.get(0)));
			}
		});
	}

	@Override
	public void onArrived() {
		final RouteOption route = MTRNavClient.JOURNEY.getActiveRoute();
		if (route != null) {
			lastCompletedTrip = TripSummary.capture(route, MTRNavClient.JOURNEY);
			lastSavedReportMessage = null;
			currentTripRating = 0;
			view = View.TRIP_COMPLETE;
		} else {
			view = View.RESULTS;
		}
	}
}
