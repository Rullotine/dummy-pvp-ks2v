package com.shubh.practicedummy.client.dummy;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundEvents;

/**
 * "Arcade" sound pack: layered vanilla sounds with combo-scaled pitch (rising ding per hit, bell on crits,
 * fanfare on pops and kills). Real .ogg files would need an asset pack, so this is built from vanilla sounds.
 */
public final class DummySounds {
	private DummySounds() {}

	private static boolean on() {
		return "arcade".equals(DummySettings.sounds);
	}

	private static void at(float vol, float pitch, Object sound) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld w = mc.world;
		ClientPlayerEntity p = mc.player;
		if (w == null || p == null) return;
		if (sound instanceof net.minecraft.sound.SoundEvent e) DummyCombat.playAt(w, p.getX(), p.getY(), p.getZ(), e, vol, pitch);
		else if (sound instanceof net.minecraft.registry.entry.RegistryEntry<?> r && r.value() instanceof net.minecraft.sound.SoundEvent e2) {
			DummyCombat.playAt(w, p.getX(), p.getY(), p.getZ(), e2, vol, pitch);
		}
	}

	public static void hit(int combo, boolean crit, boolean smash) {
		if (!on()) return;
		at(0.55F, 0.8F + Math.min(combo, 12) * 0.07F, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP);
		if (crit) at(0.5F, 1.7F, SoundEvents.BLOCK_NOTE_BLOCK_BELL);
		if (smash) {
			at(0.6F, 1.5F, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER);
			at(0.5F, 1.2F, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST);
		}
	}

	public static void pop() {
		if (!on()) return;
		at(0.7F, 1.4F, SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE);
		at(0.6F, 1.1F, SoundEvents.ENTITY_PLAYER_LEVELUP);
	}

	public static void kill(int streak) {
		if (!on()) return;
		at(0.8F, 0.7F + Math.min(streak, 6) * 0.08F, SoundEvents.ENTITY_PLAYER_LEVELUP);
		at(0.8F, 1.0F, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST);
		at(0.7F, 0.9F, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER);
	}

	public static void cue() {
		if (!on()) return;
		at(0.9F, 2.0F, SoundEvents.BLOCK_NOTE_BLOCK_BELL);
	}

	public static void goal() {
		if (!on()) return;
		at(0.8F, 1.3F, SoundEvents.ENTITY_PLAYER_LEVELUP);
	}
}
