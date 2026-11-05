package com.shubh.practicedummy.client.dummy;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Armor for the dummy: visuals plus the vanilla armor / toughness numbers used for damage reduction. */
public enum ArmorSet {
	NONE(0.0F, 0.0F, null, null, null, null),
	IRON(15.0F, 0.0F, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS),
	DIAMOND(20.0F, 8.0F, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS),
	NETHERITE(20.0F, 12.0F, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);

	public final float armor;
	public final float toughness;
	private final Item head, chest, legs, feet;

	ArmorSet(float armor, float toughness, Item head, Item chest, Item legs, Item feet) {
		this.armor = armor;
		this.toughness = toughness;
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
		dummy.setArmorStats(armor, toughness);
	}

	private static ItemStack stack(Item item) {
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}
}
