package com.mtrnav.client;

import com.mtrnav.MTRNavClient;
import com.mtrnav.journey.JourneyManager;
import com.mtrnav.phone.GuiDraw;
import com.mtrnav.phone.PhoneScreen;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;

/**
 * The small bottom-left journey widget shown whenever a journey is active
 * (phone open or closed). Three rows:
 * <pre>
 * To: Lotteton Central              <- destination, or next stop while riding
 * Go to Townsend Farm Rd/Goole St   <- the single current instruction
 * 56 minutes | 17:05 - 18:01        <- live countdown | fixed departure-arrival clock
 * </pre>
 * If the line being waited for is running late, the top row is replaced by
 * a scrolling "Delay: ..." message instead of the "To:" line.
 */
public final class JourneyHudOverlay implements HudRenderCallback {

	private static final int WIDGET_WIDTH = 160;
	private static final int WIDGET_HEIGHT = 42;
	private static final int MARGIN = 8;
	private static final int MAX_SCROLL_CHARS = 24;

	@Override
	public void onHudRender(MatrixStack matrices, float tickDelta) {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.currentScreen instanceof PhoneScreen) {
			return;
		}
		final JourneyManager journey = MTRNavClient.JOURNEY;
		if (!journey.isActive()) {
			return;
		}

		final int screenHeight = client.getWindow().getScaledHeight();
		final int x = MARGIN;
		final int y = screenHeight - WIDGET_HEIGHT - MARGIN;

		GuiDraw.fill(matrices, x, y, x + WIDGET_WIDTH, y + WIDGET_HEIGHT, 0xCC101014);

		final TextRenderer textRenderer = client.textRenderer;
		final int textX = x + 6;

		if (journey.isDelayed()) {
			final long deviationSeconds = Math.abs(journey.currentDelayMillis()) / 1000;
			final String delayText = "Delay: " + (deviationSeconds / 60) + "m " + (deviationSeconds % 60) + "s";
			GuiDraw.drawTextWithShadow(matrices, textRenderer, scrollingWindow(delayText, MAX_SCROLL_CHARS), textX, y + 5, 0xFF5555);
		} else {
			GuiDraw.drawTextWithShadow(matrices, textRenderer, "To: " + journey.toLabel(), textX, y + 5, 0xFFFFFF);
		}

		GuiDraw.drawTextWithShadow(matrices, textRenderer, journey.instructionText(), textX, y + 18, 0xCCFFCC);

		final String bottomLine = journey.remainingMinutes() + " minutes | " + journey.scheduleClockRange();
		GuiDraw.drawTextWithShadow(matrices, textRenderer, bottomLine, textX, y + 30, 0x999999);
	}

	/** Chunked (not smooth-pixel) scrolling: swaps which slice of the text is shown every 200ms. Avoids needing a GL scissor region. */
	private static String scrollingWindow(String fullText, int maxChars) {
		if (fullText.length() <= maxChars) {
			return fullText;
		}
		final String padded = fullText + "     ";
		final int totalLength = padded.length();
		final int shift = (int) ((System.currentTimeMillis() / 200) % totalLength);
		final String doubled = padded + padded;
		return doubled.substring(shift, shift + maxChars);
	}
}
