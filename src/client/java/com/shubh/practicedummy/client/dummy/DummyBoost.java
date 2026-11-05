package com.shubh.practicedummy.client.dummy;

import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Mace-smash launch (Wind Burst) and fall-damage cancel.
 *
 * - Single-player: the launch is applied directly (the integrated server just accepts it).
 * - Multiplayer: the client never forces its own velocity. If you turn on /dummy boost server on and
 *   you have command permission there (op / your own server), it asks the SERVER to launch you by
 *   summoning real wind charges under you, so the server itself grants the boost (fall-damage rules
 *   and anti-cheats treat it like any wind charge). Without permission the server just says no.
 */
public final class DummyBoost {
	/** Upward launch speed per Wind Burst level (0 = no enchant: just a tiny bounce). Approximate. */
	private static final double[] LAUNCH = {0.01, 1.25, 1.55, 1.85};
	private static final String[] ROMAN = {"", "I", "II", "III"};

	private static boolean noFall;
	private static int groundTicks;
	private static boolean warnedMultiplayer;
	private static int lastServerBoostTick = -100;

	private DummyBoost() {}

	/** Returns a short readout tag, or null. */
	public static String onSmash(MinecraftClient mc, ClientPlayerEntity player) {
		if (!DummySettings.boost) return null;
		if (!mc.isInSingleplayer()) {
			if (DummySettings.serverBoost && DummySettings.windBurst > 0) return askServer(mc, player);
			if (warnedMultiplayer) return null;
			warnedMultiplayer = true;
			return "boost: single-player only (see /dummy boost server)";
		}
		int level = Math.max(0, Math.min(3, DummySettings.windBurst));
		Vec3d v = player.getVelocity();
		player.setVelocity(v.x, LAUNCH[level], v.z);
		player.fallDistance = 0.0;
		noFall = true;
		groundTicks = 0;
		return level == 0 ? "SMASH BOUNCE" : "WIND BURST " + ROMAN[level];
	}

	/** Multiplayer: have the server summon wind charges just under you; they hit you and explode. */
	private static String askServer(MinecraftClient mc, ClientPlayerEntity player) {
		if (mc.getNetworkHandler() == null) return null;
		int now = player.age;
		if (now - lastServerBoostTick < 15) return null; // don't spam commands
		lastServerBoostTick = now;
		int level = Math.max(1, Math.min(3, DummySettings.windBurst));
		for (int i = 0; i < level; i++) {
			mc.getNetworkHandler().sendChatCommand("summon minecraft:wind_charge ~ ~-1.2 ~ {Motion:[0.0,1.2,0.0]}");
		}
		return "WIND BURST " + ROMAN[level] + " (server)";
	}

	/** Keeps the integrated server's fall distance at 0 until you land, so the boost doesn't hurt. */
	public static void tick(MinecraftClient mc) {
		if (!noFall) return;
		ClientPlayerEntity player = mc.player;
		IntegratedServer server = mc.getServer();
		if (player == null || server == null) {
			noFall = false;
			return;
		}
		UUID id = player.getUuid();
		server.execute(() -> {
			ServerPlayerEntity sp = server.getPlayerManager().getPlayer(id);
			if (sp != null) sp.fallDistance = 0.0;
		});
		player.fallDistance = 0.0;
		if (player.isOnGround()) {
			if (++groundTicks > 2) noFall = false;
		} else {
			groundTicks = 0;
		}
	}

	public static void reset() {
		noFall = false;
	}
}
