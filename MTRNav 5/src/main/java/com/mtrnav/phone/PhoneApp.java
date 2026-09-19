package com.mtrnav.phone;

import net.minecraft.client.util.math.MatrixStack;

/**
 * One app on the phone's home screen. The app owns everything inside the
 * content area {@link PhoneScreen} gives it; the phone chrome (rounded
 * panel, persistent Back/Home buttons) is drawn by {@link PhoneScreen}
 * itself and never by the app.
 */
public interface PhoneApp {

	String title();

	/** Called whenever the app becomes the visible one, including re-entering after Back. */
	default void onOpen(PhoneScreen phone) {
	}

	/** Called every screen tick while visible. */
	default void tick(PhoneScreen phone) {
	}

	void render(PhoneScreen phone, MatrixStack matrices, int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks);

	/** @return true if the app handled its own "back" (e.g. closing an internal picker); false to let the phone go Home. */
	default boolean onBack(PhoneScreen phone) {
		return false;
	}

	default boolean mouseClicked(PhoneScreen phone, int x, int y, int width, int height, double mouseX, double mouseY, int button) {
		return false;
	}

	default boolean mouseScrolled(PhoneScreen phone, int x, int y, int width, int height, double mouseX, double mouseY, double amount) {
		return false;
	}
}
