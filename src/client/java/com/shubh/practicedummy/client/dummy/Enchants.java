package com.shubh.practicedummy.client.dummy;

import java.util.Optional;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;

/** The enchant levels on a stack that matter for hitting the dummy. */
public final class Enchants {
	public int breach;
	public int density;
	public int sharpness;
	public int knockback;
	public int windBurst;

	private Enchants() {}

	public static Enchants of(ItemStack stack) {
		Enchants result = new Enchants();
		if (stack == null || stack.isEmpty()) return result;
		ItemEnchantmentsComponent component = stack.getEnchantments();
		for (RegistryEntry<Enchantment> entry : component.getEnchantments()) {
			Optional<RegistryKey<Enchantment>> key = entry.getKey();
			if (key.isEmpty()) continue;
			int level = component.getLevel(entry);
			switch (key.get().getValue().getPath()) {
				case "breach" -> result.breach = level;
				case "density" -> result.density = level;
				case "sharpness" -> result.sharpness = level;
				case "knockback" -> result.knockback = level;
				case "wind_burst" -> result.windBurst = level;
				default -> { }
			}
		}
		return result;
	}
}
