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

	/** Protection enchant level on the dummy's armor (0-4). Each level = 4 EPF, capped at 20 like vanilla. */
	public static int protection = 0;

	/** Enchant levels that act as if your weapon had them (the higher of this and the real enchant is used). */
	public static int simBreach = 0;
	public static int simDensity = 0;
	public static int simSharpness = 0;

	/** Wind Burst level for the mace-smash launch when your mace has no real Wind Burst (0 = small bounce). */
	public static int windBurst = 2;
	/** Mace boost on/off. Direct launch only ever applies in single-player worlds. */
	public static boolean boost = true;
	/** Multiplayer: ask the SERVER for the launch (real wind charges via /summon). Needs command permission. */
	public static boolean serverBoost = false;
	/** Also kick you forward along your look direction on a smash (single-player). */
	public static boolean windLook = false;

	/** Shield stun length after an axe hit, in ticks (vanilla axe = 5 seconds). */
	public static int stunTicks = 100;

	/** Show the stats panel and the bars over the dummy. */
	public static boolean hud = true;

	private DummySettings() {}
}
