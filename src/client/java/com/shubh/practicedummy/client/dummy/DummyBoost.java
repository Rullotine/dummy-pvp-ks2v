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
 * Deliberately SINGLE-PLAYER ONLY: on a multiplayer server these velocity changes are things the
 * server never granted you, which anti-cheats read as fly/velocity hacks (and it would be a free
 * super-jump on servers without one). In single-player the integrated server simply accepts it.
 */
public final class DummyBoost {
	/** Upward launch speed per Wind Burst level (0 = no enchant: just a tiny bounce). Approximate. */
	private static final double[] LAUNCH = {0.01, 1.25, 1.55, 1.85};
	private static final String[] ROMAN = {"", "I", "II", "III"};

	private static boolean noFall;
	private static int groundTicks;
	private static boolean warnedMultiplayer;

	private DummyBoost() {}

	/** Returns a short readout tag, or null. */
	public static String onSmash(MinecraftClient mc, ClientPlayerEntity player) {
		if (!DummySettings.boost) return null;
		if (!mc.isInSingleplayer()) {
			if (warnedMultiplayer) return null;
			warnedMultiplayer = true;
			return "boost: single-player only";
		}
		int level = Math.max(0, Math.min(3, DummySettings.windBurst));
		Vec3d v = player.getVelocity();
		player.setVelocity(v.x, LAUNCH[level], v.z);
		player.fallDistance = 0.0;
		noFall = true;
		groundTicks = 0;
		return level == 0 ? "SMASH BOUNCE" : "WIND BURST " + ROMAN[level];
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
