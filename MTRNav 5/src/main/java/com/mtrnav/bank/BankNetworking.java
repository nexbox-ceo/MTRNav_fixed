package com.mtrnav.bank;

import com.mtrnav.item.ModItems;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Wire format for the Bank app. Inventory changes only ever happen here, on
 * the server, in response to a validated request -- the client never
 * mutates its own inventory directly (that would just get overwritten by
 * the next server sync, or worse, desync).
 */
public final class BankNetworking {

	public static final Identifier ACTION_CHANNEL = new Identifier("mtrnav", "bank_action");
	public static final Identifier SYNC_CHANNEL = new Identifier("mtrnav", "bank_sync");

	public static final byte ACTION_KEEP = 1;
	public static final byte ACTION_CASH_OUT_SAVED = 2;
	public static final byte ACTION_REQUEST_SYNC = 3;

	private BankNetworking() {
	}

	public static void registerServerReceiver() {
		ServerPlayNetworking.registerGlobalReceiver(ACTION_CHANNEL, (server, player, handler, buf, responseSender) -> {
			final byte action = buf.readByte();
			server.execute(() -> handleAction(player, action));
		});
	}

	private static void handleAction(ServerPlayerEntity player, byte action) {
		switch (action) {
			case ACTION_KEEP -> {
				final int emeralds = removeItems(player, Items.EMERALD, countItems(player, Items.EMERALD));
				BankWallet.adjustBalance(player.getUuid(), emeralds);
			}
			case ACTION_CASH_OUT_SAVED -> {
				final long balance = BankWallet.getBalance(player.getUuid());
				BankWallet.adjustBalance(player.getUuid(), -balance);
				giveTickets(player, (int) balance);
			}
			case ACTION_REQUEST_SYNC -> {
				// No inventory change -- just fall through to the sync below.
			}
			default -> {
				return;
			}
		}
		sendSync(player);
	}

	public static void sendSync(ServerPlayerEntity player) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeLong(BankWallet.getBalance(player.getUuid()));
		ServerPlayNetworking.send(player, SYNC_CHANNEL, buf);
	}

	private static int countItems(ServerPlayerEntity player, net.minecraft.item.Item item) {
		int total = 0;
		for (final ItemStack stack : player.getInventory().main) {
			if (stack.getItem() == item) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static int removeItems(ServerPlayerEntity player, net.minecraft.item.Item item, int amount) {
		int remaining = amount;
		for (int i = 0; i < player.getInventory().main.size() && remaining > 0; i++) {
			final ItemStack stack = player.getInventory().main.get(i);
			if (stack.getItem() != item) {
				continue;
			}
			final int take = Math.min(remaining, stack.getCount());
			stack.decrement(take);
			remaining -= take;
		}
		return amount - remaining;
	}

	private static void giveTickets(ServerPlayerEntity player, int amount) {
		int remaining = amount;
		while (remaining > 0) {
			final int stackSize = Math.min(64, remaining);
			final ItemStack ticketStack = new ItemStack(ModItems.TICKET, stackSize);
			if (!player.getInventory().insertStack(ticketStack)) {
				player.dropItem(ticketStack, false);
			}
			remaining -= stackSize;
		}
	}
}
