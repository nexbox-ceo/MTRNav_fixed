package com.mtrnav.client;

import com.mtrnav.phone.PhoneScreen;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {

	private static KeyBinding openPhone;

	private KeyBindings() {
	}

	public static void register() {
		openPhone = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.mtrnav.open_phone",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_P,
				"category.mtrnav.main"
		));
	}

	public static void tick(MinecraftClient client) {
		if (client.player == null) {
			return;
		}
		while (openPhone.wasPressed()) {
			if (client.currentScreen == null) {
				client.setScreen(new PhoneScreen());
			}
		}
	}
}
