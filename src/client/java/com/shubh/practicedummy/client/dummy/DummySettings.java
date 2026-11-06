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
	/** Wind Burst level for the smash launch: 0-3 = forced (default 2), -1 = auto (read it from the mace you hold). */
	public static int windBurst = 2;
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
	/** Reaction drill: shield randomly drops; hit it as fast as you can. */
	public static boolean drill = false;
	/** Dummy's swings deal simulated damage to you (blocked by your shield). */
	public static boolean hitback = false;
	/** Show sprint-hit / w-tap feedback. */
	public static boolean wtap = true;
	/** Dummy throws simulated ender pearls and teleports. */
	public static boolean pearl = false;
	/** Dummy launches itself with wind charges. */
	public static boolean windCharge = false;
	public static boolean respawn = true;
	/** The dummy trash-talks (floating text) when it pops, dies or respawns. */
	public static boolean taunts = true;
	/** Totems per life: -1 = infinite, otherwise it dies after that many pops. */
	public static int totems = -1;
	/** Ticks the dummy needs to re-equip a totem after a pop; lethal damage inside that window kills it. */
	public static int refill = 20;
	/** Combo length to celebrate (0 = off). */
	public static int comboGoal = 0;
	/** "arcade" = layered custom-style sounds (combo-pitch dings etc.), "vanilla" = plain. */
	public static String sounds = "arcade";

	private DummySettings() {}
}
