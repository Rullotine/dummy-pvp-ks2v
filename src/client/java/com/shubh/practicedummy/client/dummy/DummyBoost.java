package com.shubh.practicedummy.client.dummy;

import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * Mace-smash launch (Wind Burst), fall-damage cancel and the "peak height" readout.
 *
 * - Single-player: the launch is applied directly (the integrated server just accepts it).
 * - Multiplayer: the client never forces its own velocity. If you turn on /dummy boost server on and
 *   you have command permission there (op / your own server), it asks the SERVER to launch you by
 *   summoning real wind charges under you, so the server itself grants the boost.
 */
public final class DummyBoost {
	/** Upward launch speed per Wind Burst level (0 = no enchant: just a tiny bounce). Approximate. */
	private static final double[] LAUNCH = {0.01, 1.25, 1.55, 1.85};
	private static final String[] ROMAN = {"", "I", "II", "III"};

	private static boolean noFall;
	private static int groundTicks;
	private static boolean warnedMultiplayer;
	private static int lastServerBoostTick = -100;

	private static boolean trackingPeak;
	private static double peakStartY;
	private static double peakY;

	private DummyBoost() {}

	/** Returns a short readout tag, or null. */
	public static String onSmash(MinecraftClient mc, ClientPlayerEntity player, int level, double fall) {
		if (!DummySettings.boost) return null;
		level = Math.max(0, Math.min(3, level));
		if (!mc.isInSingleplayer()) {
			if (DummySettings.serverBoost && level > 0) return askServer(mc, player, level);
			if (warnedMultiplayer) return null;
			warnedMultiplayer = true;
			return "boost: single-player only (see /dummy boost server)";
		}

		Vec3d v = player.getVelocity();
		double hx = 0.0, hz = 0.0;
		if (DummySettings.windLook && level > 0) { // chain-friendly: shove you forward too
			Vec3d look = player.getRotationVector();
			hx = look.x * 0.35 * level;
			hz = look.z * 0.35 * level;
		}
		player.setVelocity(v.x + hx, LAUNCH[level], v.z + hz);
		player.fallDistance = 0.0;
		noFall = true;
		groundTicks = 0;

		// peak-height tracking for the readout
		trackingPeak = level > 0;
		peakStartY = player.getY();
		peakY = peakStartY;

		// flashy gust + sound
		ClientWorld world = mc.world;
		if (world != null && level > 0) {
			world.addParticleClient(level >= 2 ? ParticleTypes.GUST_EMITTER_LARGE : ParticleTypes.GUST_EMITTER_SMALL,
				false, false, player.getX(), player.getY() + 0.1, player.getZ(), 0.0, 0.0, 0.0);
			world.playSoundClient(player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST, SoundCategory.PLAYERS, 1.0F, 0.9F + level * 0.1F, false);
		}
		return level == 0 ? "SMASH BOUNCE" : "WIND BURST " + ROMAN[level];
	}

	/** Multiplayer: have the server summon wind charges just under you; they hit you and explode. */
	private static String askServer(MinecraftClient mc, ClientPlayerEntity player, int level) {
		if (mc.getNetworkHandler() == null) return null;
		int now = player.age;
		if (now - lastServerBoostTick < 15) return null; // don't spam commands
		lastServerBoostTick = now;
		for (int i = 0; i < level; i++) {
			mc.getNetworkHandler().sendChatCommand("summon minecraft:wind_charge ~ ~-1.2 ~ {Motion:[0.0,1.2,0.0]}");
		}
		return "WIND BURST " + ROMAN[level] + " (server)";
	}

	public static void tick(MinecraftClient mc) {
		ClientPlayerEntity player = mc.player;
		if (player == null) {
			noFall = false;
			trackingPeak = false;
			return;
		}

		if (trackingPeak) {
			if (player.getY() > peakY) peakY = player.getY();
			boolean falling = player.getVelocity().y <= 0.0 && peakY > peakStartY + 0.5;
			if (falling || player.isOnGround() && peakY > peakStartY + 0.5) {
				trackingPeak = false;
				if (mc.inGameHud != null) {
					mc.inGameHud.setOverlayMessage(Text.literal("\u00a7bWind Burst peak: +"
						+ String.format(Locale.ROOT, "%.1f", peakY - peakStartY) + " blocks"), false);
				}
			}
		}

		if (!noFall) return;
		IntegratedServer server = mc.getServer();
		if (server == null) {
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
		trackingPeak = false;
	}
}
