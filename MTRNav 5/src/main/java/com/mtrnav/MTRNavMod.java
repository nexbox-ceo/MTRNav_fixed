package com.mtrnav;

import com.mtrnav.bank.BankNetworking;
import com.mtrnav.dynmap.DynmapIntegration;
import com.mtrnav.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Runs on both client and dedicated server. The Smartphone/Ticket items need
 * to exist in the registry on both sides (item IDs must match); the Bank
 * app's networking is server-authoritative (see {@link BankNetworking});
 * the optional Dynmap bridge is server-side only and is skipped entirely if
 * Dynmap isn't installed (see {@link DynmapIntegration}).
 */
public final class MTRNavMod implements ModInitializer {

	@Override
	public void onInitialize() {
		ModItems.register();
		BankNetworking.registerServerReceiver();
		if (DynmapIntegration.isDynmapPresent()) {
			ServerLifecycleEvents.SERVER_STARTED.register(DynmapIntegration::register);
		}
	}
}
