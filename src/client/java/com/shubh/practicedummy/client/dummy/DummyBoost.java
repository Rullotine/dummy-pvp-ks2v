package com.shubh.practicedummy.client.dummy;

import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * Mace-smash launch (Wind Burst) and fall-damage cancel.
 * Level is read from the mace you hold (auto) or forced with /dummy windburst.
 * Single-player applies the launch directly; multiplayer only works through /dummy boost server.
 */
public final class DummyBoost {
	private static final double[] LAUNCH = {0.01, 1.25, 1.55, 1.85};

	private static boolean noFall;
	private static int groundTicks;
	private static boolean warnedMultiplayer;
	private static int lastServerBoostTick = -100;

	// HUD info
	private static int chain;
	private static double startY, peakY;
	private static int hudTicks;
	private static int usedLevel;

	private DummyBoost() {}

	public static int resolveLevel(ClientPlayerEntity player) {
		int level = DummySettings.windBurst >= 0
			? DummySettings.windBurst
			: DummyCombat.enchantLevel(player.getMainHandStack(), Enchantments.WIND_BURST);
		return Math.max(0, Math.min(3, level));
	}

	public static String levelLabel(ClientPlayerEntity player) {
		int l = resolveLevel(player);
		return (l == 0 ? "no Wind Burst" : "Wind Burst " + DummyCombat.roman(l)) + (DummySettings.windBurst < 0 ? " (auto)" : "");
	}

	public static int getChain() { return chain; }
	public static int getHudTicks() { return hudTicks; }
	public static int getUsedLevel() { return usedLevel; }
	public static double getPeakHeight() { return Math.max(0.0, peakY - startY); }

	public static String onSmash(MinecraftClient mc, ClientPlayerEntity player) {
		if (!DummySettings.boost) return null;
		int level = resolveLevel(player);
		if (!mc.isInSingleplayer()) {
			if (DummySettings.serverBoost && level > 0) return askServer(mc, player, level);
			if (warnedMultiplayer) return null;
			warnedMultiplayer = true;
			return "boost: single-player only (see /dummy boost server)";
		}
		boolean chained = !player.isOnGround() && noFall;
		chain = chained ? chain + 1 : 1;
		Vec3d v = player.getVelocity();
		player.setVelocity(v.x, LAUNCH[level], v.z);
		player.fallDistance = 0.0;
		noFall = true;
		groundTicks = 0;
		startY = player.getY();
		peakY = startY;
		usedLevel = level;
		hudTicks = 120;
		burstFx(mc.world, player, level);
		String tag = level == 0 ? "SMASH BOUNCE" : "WIND BURST " + DummyCombat.roman(level);
		return chain > 1 ? tag + " \u00a7dCHAIN x" + chain : tag;
	}

	private static void burstFx(ClientWorld world, ClientPlayerEntity p, int level) {
		if (world == null || level == 0) return;
		world.playSoundClient(p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY,
			SoundCategory.PLAYERS, 1.0F, 1.3F, false);
		int n = 14 + level * 8;
		for (int i = 0; i < n; i++) {
			double a = i / (double) n * Math.PI * 2.0;
			double sp = 0.25 + 0.08 * level;
			world.addParticleClient(ParticleTypes.CLOUD, false, false, p.getX(), p.getY() + 0.1, p.getZ(),
				Math.cos(a) * sp, 0.05, Math.sin(a) * sp);
			world.addParticleClient(ParticleTypes.POOF, false, false, p.getX(), p.getY() + 0.1, p.getZ(),
				Math.cos(a) * sp * 0.5, 0.2 + 0.05 * level, Math.sin(a) * sp * 0.5);
		}
	}

	private static String askServer(MinecraftClient mc, ClientPlayerEntity player, int level) {
		if (mc.getNetworkHandler() == null) return null;
		int now = player.age;
		if (now - lastServerBoostTick < 15) return null;
		lastServerBoostTick = now;
		for (int i = 0; i < level; i++) {
			mc.getNetworkHandler().sendChatCommand("summon minecraft:wind_charge ~ ~-1.2 ~ {Motion:[0.0,1.2,0.0]}");
		}
		return "WIND BURST " + DummyCombat.roman(level) + " (server)";
	}

	public static void tick(MinecraftClient mc) {
		if (hudTicks > 0) hudTicks--;
		if (!noFall) return;
		ClientPlayerEntity player = mc.player;
		IntegratedServer server = mc.getServer();
		if (player == null || server == null) {
			noFall = false;
			return;
		}
		peakY = Math.max(peakY, player.getY());
		UUID id = player.getUuid();
		server.execute(() -> {
			ServerPlayerEntity sp = server.getPlayerManager().getPlayer(id);
			if (sp != null) sp.fallDistance = 0.0;
		});
		player.fallDistance = 0.0;
		if (player.isOnGround()) {
			if (++groundTicks > 2) {
				noFall = false;
				chain = 0;
			}
		} else {
			groundTicks = 0;
		}
	}

	public static void reset() {
		noFall = false;
		chain = 0;
		hudTicks = 0;
	}

	public static String heightText() {
		return String.format(Locale.ROOT, "%.1f", getPeakHeight());
	}
}
