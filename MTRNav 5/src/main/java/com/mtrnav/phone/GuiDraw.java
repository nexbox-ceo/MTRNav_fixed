package com.mtrnav.phone;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;

/**
 * {@link DrawableHelper}'s fill/text methods are {@code protected}, only
 * reachable from a subclass (like {@link PhoneScreen} itself). Every
 * {@link PhoneApp} implementation lives in its own class that can't extend
 * DrawableHelper (it already isn't a Screen), so this class extends it once
 * and re-exposes what's needed as public statics for apps to call.
 */
public final class GuiDraw extends DrawableHelper {

	private GuiDraw() {
	}

	public static void fill(MatrixStack matrices, int x1, int y1, int x2, int y2, int color) {
		DrawableHelper.fill(matrices, x1, y1, x2, y2, color);
	}

	public static void drawCenteredText(MatrixStack matrices, TextRenderer textRenderer, String text, int centerX, int y, int color) {
		DrawableHelper.drawCenteredText(matrices, textRenderer, text, centerX, y, color);
	}

	public static void drawTextWithShadow(MatrixStack matrices, TextRenderer textRenderer, String text, int x, int y, int color) {
		DrawableHelper.drawTextWithShadow(matrices, textRenderer, text, x, y, color);
	}
}
