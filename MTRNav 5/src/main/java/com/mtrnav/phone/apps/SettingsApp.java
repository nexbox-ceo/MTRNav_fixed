package com.mtrnav.phone.apps;

import com.mtrnav.phone.GuiDraw;
import com.mtrnav.phone.PhoneApp;
import com.mtrnav.phone.PhoneScreen;
import com.mtrnav.tts.TtsConfig;
import com.mtrnav.tts.TtsEngine;
import com.mtrnav.tts.TtsVoice;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Voice picker. This lives on the phone rather than in vanilla's own Options
 * screen: adding a row to Minecraft's real OptionsScreen needs a Mixin
 * targeting that exact class, which this build had no way to compile-verify
 * against the real (Loom-remapped) 1.18.2 jar -- see README.md. A phone app
 * is just as reachable and doesn't risk a fragile, unverified Mixin.
 */
public final class SettingsApp implements PhoneApp {

	private static final int ROW_HEIGHT = 22;

	@Override
	public String title() {
		return "Settings";
	}

	@Override
	public void render(PhoneScreen phone, MatrixStack matrices, int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks) {
		final MinecraftClient client = MinecraftClient.getInstance();
		GuiDraw.drawTextWithShadow(matrices, client.textRenderer, "Announcement voice", x + 10, y + 6, 0x888888);

		int rowY = y + 20;
		for (final TtsVoice voice : TtsVoice.values()) {
			final boolean selected = TtsEngine.currentVoice == voice;
			final boolean hovered = mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 4 && mouseX >= x + 8 && mouseX <= x + width - 8;
			GuiDraw.fill(matrices, x + 8, rowY, x + width - 8, rowY + ROW_HEIGHT - 4, selected ? 0xFF2E4A2E : hovered ? 0xFF26262E : 0xFF1A1A1F);
			GuiDraw.drawTextWithShadow(matrices, client.textRenderer, (selected ? "\u2713 " : "   ") + voice.label, x + 14, rowY + 6, selected ? 0xAAFFAA : 0xDDDDDD);
			rowY += ROW_HEIGHT;
		}

		GuiDraw.drawTextWithShadow(matrices, client.textRenderer, "Uses your OS's built-in voices --", x + 10, y + height - 28, 0x777777);
		GuiDraw.drawTextWithShadow(matrices, client.textRenderer, "no setup needed", x + 10, y + height - 18, 0x777777);
	}

	@Override
	public boolean mouseClicked(PhoneScreen phone, int x, int y, int width, int height, double mouseX, double mouseY, int button) {
		int rowY = y + 20;
		for (final TtsVoice voice : TtsVoice.values()) {
			if (mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 4 && mouseX >= x + 8 && mouseX <= x + width - 8) {
				TtsEngine.currentVoice = voice;
				TtsConfig.save();
				if (voice != TtsVoice.NONE) {
					TtsEngine.speak("Voice set to " + voice.label);
				}
				return true;
			}
			rowY += ROW_HEIGHT;
		}
		return false;
	}
}
