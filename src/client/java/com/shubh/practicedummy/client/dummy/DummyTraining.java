package com.shubh.practicedummy.client.dummy;

import java.util.ArrayDeque;
import java.util.Locale;

/** Challenge timer, reaction drill, sprint trainer, kill streaks and the fight history. */
public final class DummyTraining {
	private static int tickCount;

	// challenge
	private static boolean challengeActive;
	private static int challengeTicksLeft, challengeHits;
	private static float challengeDamage, bestChallengeDamage;
	private static int bestChallengeHits;

	// drill
	private static int reactions, reactionTicksSum, bestReaction = -1, missedCues;

	// sprint trainer / crit rate
	private static int meleeHits, sprintHits;

	// kills
	private static int kills, streak, lastKillTick = -10_000;
	private static int comboGoalsDone;

	private static final ArrayDeque<String> HISTORY = new ArrayDeque<>();

	private DummyTraining() {}

	public static void tick() {
		tickCount++;
		if (challengeActive && --challengeTicksLeft <= 0) finishChallenge();
	}

	// ----- challenge
	public static void startChallenge(int seconds) {
		challengeActive = true;
		challengeTicksLeft = seconds * 20;
		challengeHits = 0;
		challengeDamage = 0.0F;
		DummyHud.popBanner("GO!", seconds + " second challenge", 0x55FF55);
	}

	public static void stopChallenge() {
		challengeActive = false;
	}

	public static boolean isChallengeActive() { return challengeActive; }
	public static float getChallengeSecondsLeft() { return challengeTicksLeft / 20.0F; }
	public static int getChallengeHits() { return challengeHits; }
	public static float getChallengeDamage() { return challengeDamage; }
	public static float getBestChallengeDamage() { return bestChallengeDamage; }

	private static void finishChallenge() {
		challengeActive = false;
		boolean record = challengeDamage > bestChallengeDamage;
		if (record) {
			bestChallengeDamage = challengeDamage;
			bestChallengeHits = challengeHits;
		}
		DummyHud.popBanner(record ? "NEW BEST!" : "TIME!",
			String.format(Locale.ROOT, "%d hits / %.1f damage (best %.1f)", challengeHits, challengeDamage, bestChallengeDamage),
			record ? 0xFFD700 : 0x55FF55);
		addHistory(String.format(Locale.ROOT, "challenge: %d hits, %.1f dmg%s", challengeHits, challengeDamage, record ? " (best!)" : ""));
	}

	public static void onHit(float damage) {
		if (challengeActive) {
			challengeHits++;
			challengeDamage += damage;
		}
	}

	// ----- drill
	public static void onReaction(int ticks) {
		reactions++;
		reactionTicksSum += ticks;
		if (bestReaction < 0 || ticks < bestReaction) bestReaction = ticks;
	}

	public static void onMissedCue() { missedCues++; }
	public static int getReactions() { return reactions; }
	public static int getMissedCues() { return missedCues; }
	public static int getBestReaction() { return bestReaction; }
	public static float getAvgReactionSeconds() { return reactions == 0 ? -1.0F : reactionTicksSum / (float) reactions / 20.0F; }

	// ----- sprint trainer
	public static void onMelee(boolean sprintHit) {
		meleeHits++;
		if (sprintHit) sprintHits++;
	}

	public static int getMeleeHits() { return meleeHits; }
	public static int getSprintHits() { return sprintHits; }

	// ----- kills
	public static int onKill() {
		kills++;
		streak = tickCount - lastKillTick < 300 ? streak + 1 : 1;
		lastKillTick = tickCount;
		return streak;
	}

	public static int getKills() { return kills; }
	public static int getStreak() { return streak; }
	public static void onComboGoal() { comboGoalsDone++; }
	public static int getComboGoalsDone() { return comboGoalsDone; }

	// ----- history
	public static void addHistory(String line) {
		HISTORY.addFirst(line);
		while (HISTORY.size() > 10) HISTORY.removeLast();
	}

	public static Iterable<String> history() { return HISTORY; }
	public static void clearHistory() { HISTORY.clear(); }

	public static void resetAll() {
		reactions = reactionTicksSum = missedCues = meleeHits = sprintHits = kills = streak = comboGoalsDone = 0;
		bestReaction = -1;
		lastKillTick = -10_000;
		bestChallengeDamage = 0.0F;
		bestChallengeHits = 0;
		challengeActive = false;
	}
}
