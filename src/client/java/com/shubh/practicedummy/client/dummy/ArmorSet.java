package com.shubh.practicedummy.client.dummy;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Visual-only armor for the dummy (it has infinite HP regardless). */
public enum ArmorSet {
	NONE(null, null, null, null),
	IRON(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS),
	DIAMOND(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS),
	NETHERITE(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);

	private final Item head, chest, legs, feet;

	ArmorSet(Item head, Item chest, Item legs, Item feet) {
		this.head = head;
		this.chest = chest;
		this.legs = legs;
		this.feet = feet;
	}

	public void applyTo(PracticeDummy dummy) {
		dummy.equipStack(EquipmentSlot.HEAD, stack(head));
		dummy.equipStack(EquipmentSlot.CHEST, stack(chest));
		dummy.equipStack(EquipmentSlot.LEGS, stack(legs));
		dummy.equipStack(EquipmentSlot.FEET, stack(feet));
	}

	private static ItemStack stack(Item item) {
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}
}
