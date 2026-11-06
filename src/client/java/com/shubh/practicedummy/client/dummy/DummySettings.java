package com.shubh.practicedummy.client.dummy;

import net.minecraft.entity.player.SkinTextures;

/** Session-wide settings, applied to every new dummy and (where it makes sense) existing ones. */
public final class DummySettings {
	public static String name = "Dummy";
	public static DummyMode mode = DummyMode.FIGHT;
	public static ArmorSet armor = ArmorSet.NETHERITE;
	public static HoldItem hold = HoldItem.SWORD;
	public static float maxHealth = 20.0F;
	/** null = use your own skin */
	public static SkinTextures skin = null;
	/** Wind Burst level for the smash launch: -1 = auto (read it from the mace you hold), 0-3 = forced. */
	public static int windBurst = -1;
	public static boolean boost = true;
	public static boolean serverBoost = false;
	/** Dummy swims/dives toward you in water. */
	public static boolean swim = true;
	/** Apply armor points, toughness, Protection and netherite knockback resistance to damage. */
	public static boolean armorCalc = true;
	/** Protection level (0-4) on every armor piece. */
	public static int protection = 0;
	/** Dummy heals back up when you stop hitting it. */
	public static boolean autoHeal = true;
	public static boolean glint = true;
	public static boolean hudBars = true;

	private DummySettings() {}
}
