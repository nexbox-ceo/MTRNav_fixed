package com.mtrnav.phone;

import com.mtrnav.client.SoundHelper;
import com.mtrnav.phone.apps.BankApp;
import com.mtrnav.phone.apps.MTRNavApp;
import com.mtrnav.phone.apps.SettingsApp;
import com.mtrnav.phone.apps.TimesApp;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The phone overlay. Deliberately NOT a vanilla container/inventory screen:
 * it never calls {@code renderBackground}, so the game world keeps rendering
 * (and animating/ticking) behind it exactly as before the phone opened --
 * opening this screen only unlocks the mouse cursor, which is standard
 * Minecraft behaviour for any open {@link Screen} and needs no extra code.
 *
 * Layout is a vertical 9:16 panel, centered, sized to a fraction of the
 * window so it scales cleanly across GUI-scale settings without ever
 * exceeding the window bounds.
 */
public final class PhoneScreen extends Screen {

	private static final int PANEL_COLOR = 0xEE101014;
	private static final int PANEL_BORDER = 0xFF3A3A42;
	private static final int CHROME_HEIGHT = 22;

	private final List<HomeIcon> homeIcons = List.of(
			new HomeIcon("MTRNav", MTRNavApp::new),
			new HomeIcon("Bank", BankApp::new),
			new HomeIcon("Times", TimesApp::new),
			new HomeIcon("Settings", SettingsApp::new)
	);

	@Nullable
	private PhoneApp currentApp;

	private int panelX, panelY, panelWidth, panelHeight;

	public PhoneScreen() {
		super(new LiteralText("Phone"));
		SoundHelper.playPhoneOpen();
	}

	@Override
	public void removed() {
		SoundHelper.playPhoneClose();
	}

	@Override
	protected void init() {
		// Fill most of the vertical space available, keep a strict 9:16 ratio,
		// and never exceed the window horizontally -- this is what "scales
		// cleanly across all Minecraft GUI settings" means in practice: we size
		// off the already-GUI-scaled width/height Screen gives us, not raw pixels.
		panelHeight = (int) (this.height * 0.9);
		panelWidth = panelHeight * 9 / 16;
		if (panelWidth > this.width * 0.9) {
			panelWidth = (int) (this.width * 0.9);
			panelHeight = panelWidth * 16 / 9;
		}
		panelX = (this.width - panelWidth) / 2;
		panelY = (this.height - panelHeight) / 2;
	}

	public void openApp(PhoneApp app) {
		currentApp = app;
		app.onOpen(this);
	}

	public void goHome() {
		currentApp = null;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		if (currentApp != null) {
			currentApp.tick(this);
		}
	}

	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float partialTicks) {
		// Intentionally no super.render() / renderBackground(): the world stays fully visible.
		fill(matrices, panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_COLOR);
		drawBorder(matrices);

		final int contentTop = panelY + CHROME_HEIGHT;
		final int contentHeight = panelHeight - CHROME_HEIGHT;
		drawChrome(matrices, mouseX, mouseY);

		if (currentApp == null) {
			renderHome(matrices, mouseX, mouseY, contentTop, contentHeight);
		} else {
			currentApp.render(this, matrices, panelX, contentTop, panelWidth, contentHeight, mouseX, mouseY, partialTicks);
		}
	}

	private void drawBorder(MatrixStack matrices) {
		fill(matrices, panelX, panelY, panelX + panelWidth, panelY + 1, PANEL_BORDER);
		fill(matrices, panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, PANEL_BORDER);
		fill(matrices, panelX, panelY, panelX + 1, panelY + panelHeight, PANEL_BORDER);
		fill(matrices, panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_BORDER);
	}

	private void drawChrome(MatrixStack matrices, int mouseX, int mouseY) {
		fill(matrices, panelX, panelY, panelX + panelWidth, panelY + CHROME_HEIGHT, 0xFF000000);
		final String title = currentApp == null ? "MTRNav OS" : currentApp.title();
		drawCenteredText(matrices, this.textRenderer, title, panelX + panelWidth / 2, panelY + 7, 0xFFFFFF);
		drawTextWithShadow(matrices, this.textRenderer, "<", panelX + 6, panelY + 7, isOverBack(mouseX, mouseY) ? 0xFFFF55 : 0xCCCCCC);
		drawTextWithShadow(matrices, this.textRenderer, "\u2302", panelX + panelWidth - 14, panelY + 7, isOverHome(mouseX, mouseY) ? 0xFFFF55 : 0xCCCCCC);
	}

	private void renderHome(MatrixStack matrices, int mouseX, int mouseY, int top, int height) {
		final int cellHeight = 28;
		int y = top + 10;
		for (final HomeIcon icon : homeIcons) {
			final boolean hovered = mouseX >= panelX + 10 && mouseX <= panelX + panelWidth - 10 && mouseY >= y && mouseY <= y + cellHeight - 4;
			fill(matrices, panelX + 10, y, panelX + panelWidth - 10, y + cellHeight - 4, hovered ? 0xFF2A2A34 : 0xFF1C1C22);
			drawCenteredText(matrices, this.textRenderer, icon.label, panelX + panelWidth / 2, y + cellHeight / 2 - 8, 0xFFFFFF);
			y += cellHeight;
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) {
			return false;
		}
		if (isOverBack((int) mouseX, (int) mouseY)) {
			back();
			return true;
		}
		if (isOverHome((int) mouseX, (int) mouseY)) {
			goHome();
			return true;
		}
		final int contentTop = panelY + CHROME_HEIGHT;
		final int contentHeight = panelHeight - CHROME_HEIGHT;
		if (currentApp != null) {
			return currentApp.mouseClicked(this, panelX, contentTop, panelWidth, contentHeight, mouseX, mouseY, button);
		}
		if (mouseX >= panelX && mouseX <= panelX + panelWidth) {
			final int cellHeight = 28;
			int y = contentTop + 10;
			for (final HomeIcon icon : homeIcons) {
				if (mouseY >= y && mouseY <= y + cellHeight - 4) {
					openApp(icon.factory.get());
					return true;
				}
				y += cellHeight;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (currentApp != null) {
			final int contentTop = panelY + CHROME_HEIGHT;
			final int contentHeight = panelHeight - CHROME_HEIGHT;
			return currentApp.mouseScrolled(this, panelX, contentTop, panelWidth, contentHeight, mouseX, mouseY, amount);
		}
		return false;
	}

	private void back() {
		if (currentApp != null && currentApp.onBack(this)) {
			return;
		}
		goHome();
	}

	private boolean isOverBack(int mouseX, int mouseY) {
		return mouseX >= panelX && mouseX <= panelX + 16 && mouseY >= panelY && mouseY <= panelY + CHROME_HEIGHT;
	}

	private boolean isOverHome(int mouseX, int mouseY) {
		return mouseX >= panelX + panelWidth - 18 && mouseX <= panelX + panelWidth && mouseY >= panelY && mouseY <= panelY + CHROME_HEIGHT;
	}

	private static final class HomeIcon {
		final String label;
		final java.util.function.Supplier<PhoneApp> factory;

		HomeIcon(String label, java.util.function.Supplier<PhoneApp> factory) {
			this.label = label;
			this.factory = factory;
		}
	}
}
