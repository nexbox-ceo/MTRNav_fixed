package com.mtrnav.phone.apps;

import com.mtrnav.compat.MTRDataBridge;
import com.mtrnav.phone.GuiDraw;
import com.mtrnav.phone.PhoneApp;
import com.mtrnav.phone.PhoneScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Station;
import org.mtr.core.operation.ArrivalResponse;

import java.util.ArrayList;
import java.util.List;

/** Nearest station and its live "next train" board, fed by the same cache MTR's own PIDS blocks use. */
public final class TimesApp implements PhoneApp {

	private Station nearestStation;
	private double nearestDistance;
	private List<ArrivalResponse> arrivals = List.of();
	private int refreshCooldown;

	@Override
	public String title() {
		return "Times";
	}

	@Override
	public void tick(PhoneScreen phone) {
		if (refreshCooldown-- > 0) {
			return;
		}
		refreshCooldown = 10;
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || !MTRDataBridge.isDataAvailable()) {
			nearestStation = null;
			return;
		}
		final BlockPos playerPos = client.player.getBlockPos();

		Station best = null;
		double bestDistance = Double.MAX_VALUE;
		for (final Station station : MTRDataBridge.getStations()) {
			final double d = MTRDataBridge.closestPlatformDistance(playerPos, station);
			if (d < bestDistance) {
				bestDistance = d;
				best = station;
			}
		}
		nearestStation = best;
		nearestDistance = bestDistance;

		if (nearestStation != null) {
			final List<Long> platformIds = new ArrayList<>();
			for (final Platform platform : nearestStation.savedRails) {
				platformIds.add(platform.getId());
			}
			arrivals = MTRDataBridge.requestArrivals(platformIds);
			arrivals.sort((a, b) -> Long.compare(MTRDataBridge.etaMillis(a), MTRDataBridge.etaMillis(b)));
		}
	}

	@Override
	public void render(PhoneScreen phone, MatrixStack matrices, int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks) {
		final MinecraftClient client = MinecraftClient.getInstance();
		final int centerX = x + width / 2;

		if (!MTRDataBridge.isDataAvailable()) {
			GuiDraw.drawCenteredText(matrices, client.textRenderer, "No railway data on this world", centerX, y + height / 2 - 4, 0x888888);
			return;
		}
		if (nearestStation == null) {
			GuiDraw.drawCenteredText(matrices, client.textRenderer, "Finding nearest station...", centerX, y + height / 2 - 4, 0x888888);
			return;
		}

		GuiDraw.drawCenteredText(matrices, client.textRenderer, MTRDataBridge.stationName(nearestStation), centerX, y + 10, 0xFFFFFF);
		GuiDraw.drawCenteredText(matrices, client.textRenderer, Math.round(nearestDistance) + " m away", centerX, y + 21, 0x888888);
		GuiDraw.fill(matrices, x + 8, y + 33, x + width - 8, y + 34, 0xFF333333);

		if (arrivals.isEmpty()) {
			GuiDraw.drawCenteredText(matrices, client.textRenderer, "No live predictions right now", centerX, y + 45, 0x888888);
			return;
		}

		int rowY = y + 40;
		for (final ArrivalResponse arrival : arrivals) {
			if (rowY > y + height - 10) {
				break;
			}
			final long etaSeconds = MTRDataBridge.etaMillis(arrival) / 1000;
			final String eta = etaSeconds < 30 ? "Now" : (etaSeconds / 60) + "m " + (etaSeconds % 60) + "s";
			GuiDraw.drawTextWithShadow(matrices, client.textRenderer, arrival.getRouteName(), x + 10, rowY, arrival.getRouteColor() | 0xFF000000);
			GuiDraw.drawTextWithShadow(matrices, client.textRenderer, "\u2192 " + arrival.getDestination(), x + 10, rowY + 9, 0xAAAAAA);
			GuiDraw.drawTextWithShadow(matrices, client.textRenderer, eta, x + width - 10 - client.textRenderer.getWidth(eta), rowY + 4, 0x55FF55);
			rowY += 22;
		}
	}
}
