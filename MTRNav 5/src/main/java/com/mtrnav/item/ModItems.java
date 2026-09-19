package com.mtrnav.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public final class ModItems {

	public static final Item SMARTPHONE = new SmartphoneItem(new Item.Settings().group(ItemGroup.MISC).maxCount(1));

	/**
	 * A receipt/fare token the Bank app hands out when you cash out. NOTE:
	 * MTR 4.0.5 has no item of its own for this -- its fare gates (Blocks
	 * TICKET_BARRIER_ENTRANCE_1 etc.) work some other way this build didn't
	 * dig into. This Ticket is MTRNav's own thing, not something confirmed
	 * to satisfy MTR's own ticket barriers -- see README.md.
	 */
	public static final Item TICKET = new Item(new Item.Settings().group(ItemGroup.MISC).maxCount(64));

	private ModItems() {
	}

	public static void register() {
		Registry.register(Registry.ITEM, new Identifier("mtrnav", "smartphone"), SMARTPHONE);
		Registry.register(Registry.ITEM, new Identifier("mtrnav", "ticket"), TICKET);
	}
}
