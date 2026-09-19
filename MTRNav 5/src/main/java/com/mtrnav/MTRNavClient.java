package com.mtrnav;

import com.mtrnav.bank.BankClient;
import com.mtrnav.client.JourneyHudOverlay;
import com.mtrnav.client.KeyBindings;
import com.mtrnav.compat.MTRDataBridge;
import com.mtrnav.item.SmartphoneItem;
import com.mtrnav.journey.JourneyManager;
import com.mtrnav.journey.JourneyPersistence;
import com.mtrnav.phone.PhoneScreen;
import com.mtrnav.tts.TtsConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

/**
 * The screen/HUD/routing side of MTRNav; only ever runs on the client. The
 * item itself is registered commonly by {@link MTRNavMod} (see
 * {@link SmartphoneItem} for why the "open the phone" action has to be
 * wired up from here rather than referenced directly from the item class).
 */
public final class MTRNavClient implements ClientModInitializer {

	public static final JourneyManager JOURNEY = new JourneyManager();

	private static boolean restorePending;
	private static int restoreAttemptsLeft;

	@Override
	public void onInitializeClient() {
		TtsConfig.load();
		BankClient.register();
		KeyBindings.register();
		SmartphoneItem.clientOpenAction = () -> {
			final MinecraftClient client = MinecraftClient.getInstance();
			if (client.currentScreen == null) {
				client.setScreen(new PhoneScreen());
			}
		};

		// Journey resume across a relog: on join, wait a few ticks for MTR's
		// client data to sync before attempting to resolve the saved
		// station/platform/route IDs (see JourneyPersistence).
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (JourneyPersistence.hasSavedJourney()) {
				restorePending = true;
				restoreAttemptsLeft = 100; // ~5 seconds at 20 ticks/sec, then give up
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			if (JOURNEY.isActive()) {
				JourneyPersistence.save(JOURNEY);
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			KeyBindings.tick(client);
			JOURNEY.tick();
			tickRestore();
		});
		HudRenderCallback.EVENT.register(new JourneyHudOverlay());
	}

	private static void tickRestore() {
		if (!restorePending) {
			return;
		}
		if (MTRDataBridge.isDataAvailable()) {
			JourneyPersistence.tryRestore(JOURNEY);
			restorePending = false;
		} else if (--restoreAttemptsLeft <= 0) {
			restorePending = false;
		}
	}
}
