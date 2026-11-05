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
	/** Wind Burst level used for the mace-smash launch (0 = just a small bounce). Single-player only. */
	public static int windBurst = 2;
	/** Mace boost on/off. Only ever applies in single-player worlds. */
	public static boolean boost = true;
	/**
	 * Ask the SERVER for the launch (real wind charges via /summon). Needs command permission on that
	 * server (op / your own server). Off by default.
	 */
	public static boolean serverBoost = false;

	private DummySettings() {}
}
