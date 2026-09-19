package com.mtrnav.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Right-clicking opens the phone screen. This class is loaded on BOTH client
 * and dedicated server (item registration must match on both), so it must
 * never reference a client-only class (Screen, MinecraftClient, ...)
 * directly -- not even inside an {@code if (world.isClient)} branch, since
 * that still forces the JVM to resolve the reference when this class loads
 * on the server. Instead it calls a plain {@link Runnable} that only
 * {@link com.mtrnav.MTRNavClient} (a client-only entrypoint) ever assigns.
 */
public final class SmartphoneItem extends Item {

	/** Set once by MTRNavClient#onInitializeClient; stays null on a dedicated server. */
	public static Runnable clientOpenAction;

	public SmartphoneItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		if (world.isClient && clientOpenAction != null) {
			clientOpenAction.run();
		}
		return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
	}
}
