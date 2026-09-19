package com.mtrnav.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

/**
 * UI sound effects, played detached from any world position (the standard
 * pattern for menu/HUD sounds -- {@code PositionedSoundInstance.master}).
 * Uses plain vanilla {@code SoundEvents} constants rather than anything
 * custom, so there's nothing extra to bundle or register.
 */
public final class SoundHelper {

	private SoundHelper() {
	}

	public static void playPhoneOpen() {
		play(SoundEvents.UI_BUTTON_CLICK, 1.2f);
	}

	public static void playPhoneClose() {
		play(SoundEvents.UI_BUTTON_CLICK, 0.8f);
	}

	public static void playArrivalPing() {
		play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f);
	}

	private static void play(net.minecraft.sound.SoundEvent event, float pitch) {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.getSoundManager().play(PositionedSoundInstance.master(event, pitch));
		}
	}
}
