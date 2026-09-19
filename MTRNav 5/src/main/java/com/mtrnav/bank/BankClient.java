package com.mtrnav.bank;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

/** Client side of the Bank wire format: sends requests, caches the last balance the server told us about. */
public final class BankClient {

	private static volatile long lastKnownBalance;

	private BankClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(BankNetworking.SYNC_CHANNEL, (client, handler, buf, responseSender) -> {
			final long balance = buf.readLong();
			client.execute(() -> lastKnownBalance = balance);
		});
	}

	public static long lastKnownBalance() {
		return lastKnownBalance;
	}

	public static void requestSync() {
		sendAction(BankNetworking.ACTION_REQUEST_SYNC);
	}

	public static void keep() {
		sendAction(BankNetworking.ACTION_KEEP);
	}

	public static void cashOutSaved() {
		sendAction(BankNetworking.ACTION_CASH_OUT_SAVED);
	}

	private static void sendAction(byte action) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeByte(action);
		ClientPlayNetworking.send(BankNetworking.ACTION_CHANNEL, buf);
	}
}
