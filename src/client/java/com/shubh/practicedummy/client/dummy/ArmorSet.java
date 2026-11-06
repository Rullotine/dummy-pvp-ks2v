package com.shubh.practicedummy.client.dummy;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;

/** Armor for the dummy: visuals plus vanilla armor points / toughness / knockback resistance. */
public enum ArmorSet {
	NONE(0, 0, 0.0F, null, null, null, null),
	IRON(15, 0, 0.0F, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS),
	DIAMOND(20, 8, 0.0F, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS),
	NETHERITE(20, 12, 0.4F, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);

	public final int armor;
	public final int toughness;
	public final float knockbackResist;
	private final Item head, chest, legs, feet;

	ArmorSet(int armor, int toughness, float kb, Item head, Item chest, Item legs, Item feet) {
		this.armor = armor;
		this.toughness = toughness;
		this.knockbackResist = kb;
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

	/**
	 * Vanilla armor formula. breachLevel (0-4) cuts armor effectiveness by 15% per level.
	 * Protection (full set, same level) is applied afterwards: 4% per level per piece, max 80%.
	 */
	public float reduce(float damage, int breachLevel) {
		if (damage <= 0.0F) return 0.0F;
		float a = armor, t = toughness;
		float f = 2.0F + t / 4.0F;
		float g = MathHelper.clamp(a - damage / f, a * 0.2F, 20.0F);
		float reduction = (g / 25.0F) * (1.0F - Math.min(1.0F, 0.15F * breachLevel));
		damage *= 1.0F - MathHelper.clamp(reduction, 0.0F, 1.0F);
		int epf = Math.min(20, 4 * DummySettings.protection);
		if (this != NONE) damage *= 1.0F - epf / 25.0F;
		return damage;
	}

	private static ItemStack stack(Item item) {
		if (item == null) return ItemStack.EMPTY;
		ItemStack s = new ItemStack(item);
		if (DummySettings.glint) s.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
		return s;
	}
}
