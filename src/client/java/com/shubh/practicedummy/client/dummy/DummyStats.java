package com.shubh.practicedummy.client.dummy;

import java.util.ArrayDeque;

/** Session statistics shown in the HUD panel. */
public final class DummyStats {
	private static final int DPS_WINDOW = 100; // ticks (5 s)

	public static float totalDamage;
	public static float lastDamage;
	public static int hits;
	public static int bestCombo;
	public static int totemPops;
	public static int shieldBreaks;
	public static int stunSlams;
	/** Fastest axe-stun -> mace-slam, in ticks (-1 = none yet). */
	public static int bestStunReaction = -1;

	private static int tick;
	private static final ArrayDeque<int[]> RECENT_TICKS = new ArrayDeque<>();
	private static final ArrayDeque<Float> RECENT_DAMAGE = new ArrayDeque<>();

	private DummyStats() {}

	public static void tick() {
		tick++;
		while (!RECENT_TICKS.isEmpty() && tick - RECENT_TICKS.peekFirst()[0] > DPS_WINDOW) {
			RECENT_TICKS.pollFirst();
			RECENT_DAMAGE.pollFirst();
		}
	}

	public static void recordHit(float damage, int combo) {
		hits++;
		totalDamage += damage;
		lastDamage = damage;
		if (combo > bestCombo) bestCombo = combo;
		RECENT_TICKS.addLast(new int[] {tick});
		RECENT_DAMAGE.addLast(damage);
	}

	public static float dps() {
		float sum = 0.0F;
		for (float d : RECENT_DAMAGE) sum += d;
		return sum / (DPS_WINDOW / 20.0F);
	}

	public static void recordStunSlam(int reactionTicks) {
		stunSlams++;
		if (bestStunReaction < 0 || reactionTicks < bestStunReaction) bestStunReaction = reactionTicks;
	}

	public static void reset() {
		totalDamage = 0.0F;
		lastDamage = 0.0F;
		hits = 0;
		bestCombo = 0;
		totemPops = 0;
		shieldBreaks = 0;
		stunSlams = 0;
		bestStunReaction = -1;
		RECENT_TICKS.clear();
		RECENT_DAMAGE.clear();
	}
}
