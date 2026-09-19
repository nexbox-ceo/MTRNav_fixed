package com.mtrnav.phone.apps;

import com.mtrnav.bank.BankClient;
import com.mtrnav.phone.GuiDraw;
import com.mtrnav.phone.PhoneApp;
import com.mtrnav.phone.PhoneScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Items;

/**
 * Deposit emeralds without needing MTR's own Ticket Machine block: Keep
 * moves everything in your pockets into a server-held wallet, which you
 * can only cash out into Ticket items from the Saved view -- there's no
 * immediate pocket-to-ticket shortcut, so money always passes through the
 * wallet first. All the actual emerald/ticket item movement happens
 * server-side (see {@link com.mtrnav.bank.BankNetworking}) -- this class
 * only ever displays state and sends requests.
 */
public final class BankApp implements PhoneApp {

	private enum View {MAIN, SAVED}

	private View view = View.MAIN;

	@Override
	public String title() {
		return "Bank";
	}

	@Override
	public void onOpen(PhoneScreen phone) {
		BankClient.requestSync();
	}

	@Override
	public void render(PhoneScreen phone, MatrixStack matrices, int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks) {
		if (view == View.SAVED) {
			renderSaved(matrices, x, y, width, height, mouseX, mouseY);
		} else {
			renderMain(matrices, x, y, width, height, mouseX, mouseY);
		}
	}

	private void renderMain(MatrixStack matrices, int x, int y, int width, int height, int mouseX, int mouseY) {
		final MinecraftClient client = MinecraftClient.getInstance();
		final int centerX = x + width / 2;
		final int emeralds = client.player == null ? 0 : client.player.getInventory().count(Items.EMERALD);

		GuiDraw.drawCenteredText(matrices, client.textRenderer, "In your pocket", centerX, y + 14, 0xAAAAAA);
		GuiDraw.drawCenteredText(matrices, client.textRenderer, emeralds + " emeralds", centerX, y + 28, 0x55FF55);

		drawButton(matrices, x, y + 50, width, mouseX, mouseY, "Keep", emeralds > 0);
		drawButton(matrices, x, y + 76, width, mouseX, mouseY, "Saved (" + BankClient.lastKnownBalance() + ")", true);
	}

	private void renderSaved(MatrixStack matrices, int x, int y, int width, int height, int mouseX, int mouseY) {
		final MinecraftClient client = MinecraftClient.getInstance();
		final int centerX = x + width / 2;
		final long saved = BankClient.lastKnownBalance();

		GuiDraw.drawCenteredText(matrices, client.textRenderer, "Saved", centerX, y + 14, 0xAAAAAA);
		GuiDraw.drawCenteredText(matrices, client.textRenderer, saved + " emeralds", centerX, y + 28, 0x55FF55);
		drawButton(matrices, x, y + 50, width, mouseX, mouseY, "Cash Out (Tickets)", saved > 0);
		GuiDraw.drawCenteredText(matrices, client.textRenderer, "Not spendable until cashed out", centerX, y + height - 14, 0x777777);
	}

	private void drawButton(MatrixStack matrices, int x, int rowY, int width, int mouseX, int mouseY, String label, boolean enabled) {
		final MinecraftClient client = MinecraftClient.getInstance();
		final boolean hovered = enabled && mouseY >= rowY && mouseY <= rowY + 18 && mouseX >= x + 10 && mouseX <= x + width - 10;
		GuiDraw.fill(matrices, x + 10, rowY, x + width - 10, rowY + 18, !enabled ? 0xFF1A1A1E : hovered ? 0xFF2E4A2E : 0xFF1E3A1E);
		GuiDraw.drawCenteredText(matrices, client.textRenderer, label, x + width / 2, rowY + 5, enabled ? 0xFFFFFF : 0x666666);
	}

	@Override
	public boolean mouseClicked(PhoneScreen phone, int x, int y, int width, int height, double mouseX, double mouseY, int button) {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (view == View.MAIN) {
			final int emeralds = client.player == null ? 0 : client.player.getInventory().count(Items.EMERALD);
			if (rowHit(mouseX, mouseY, x, y + 50, width) && emeralds > 0) {
				BankClient.keep();
				return true;
			}
			if (rowHit(mouseX, mouseY, x, y + 76, width)) {
				view = View.SAVED;
				return true;
			}
		} else {
			if (rowHit(mouseX, mouseY, x, y + 50, width) && BankClient.lastKnownBalance() > 0) {
				BankClient.cashOutSaved();
				return true;
			}
		}
		return false;
	}

	private boolean rowHit(double mouseX, double mouseY, int x, int rowY, int width) {
		return mouseY >= rowY && mouseY <= rowY + 18 && mouseX >= x + 10 && mouseX <= x + width - 10;
	}

	@Override
	public boolean onBack(PhoneScreen phone) {
		if (view == View.SAVED) {
			view = View.MAIN;
			return true;
		}
		return false;
	}
}
