package com.shubh.practicedummy.client.dummy;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** What the dummy holds in its main hand. Looked up by id so a missing item just means "empty". */
public enum HoldItem {
	NONE(null),
	SWORD("netherite_sword"),
	AXE("netherite_axe"),
	MACE("mace"),
	SPEAR("netherite_spear"),
	SHIELD("shield"),
	BOW("bow"),
	CROSSBOW("crossbow");

	private final String itemId;

	HoldItem(String itemId) {
		this.itemId = itemId;
	}

	public ItemStack stack() {
		if (itemId == null) return ItemStack.EMPTY;
		Item item = Registries.ITEM.get(Identifier.of("minecraft", itemId));
		if (item == Items.AIR) return ItemStack.EMPTY;
		ItemStack s = new ItemStack(item);
		if (DummySettings.glint) s.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
		return s;
	}
}
