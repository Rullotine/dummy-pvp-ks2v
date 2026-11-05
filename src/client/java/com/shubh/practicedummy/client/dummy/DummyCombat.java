package com.shubh.practicedummy.client.dummy;

import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Re-implements the parts of a vanilla player attack that matter for practice (damage, attack
 * cooldown scaling, crits, sprint knockback, sweep, mace smash) locally, because the server will
 * never process a hit on an entity it doesn't know about.
 */
public final class DummyCombat {
	/** Approximate spear jab reach in blocks. Tweak if it feels off. */
	private static final double SPEAR_REACH = 4.5;
	private static final double SPEAR_MARGIN = 0.125;
	private static final Random FX = new Random();

	private DummyCombat() {}

	public static void hit(PlayerEntity player, PracticeDummy dummy) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld world = mc.world;
		if (world == null) return;

		if (!dummy.canBeHit()) { // still inside the damage-immunity window
			player.resetTicksSinceLastAttack();
			return;
		}

		float cooldown = player.getAttackCooldownProgress(0.5F);
		boolean charged = cooldown > 0.9F;
		double fall = player.fallDistance;
		ItemStack held = player.getMainHandStack();

		boolean smash = held.isOf(Items.MACE) && fall > 1.5 && !player.isGliding();
		double base = player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE) + (smash ? smashBonus(fall) : 0.0);
		float damage = (float) base * (0.2F + cooldown * cooldown * 0.8F);

		boolean crit = charged && !smash && fall > 0.0 && !player.isOnGround() && !player.isClimbing()
			&& !player.isTouchingWater() && !player.hasStatusEffect(StatusEffects.BLINDNESS)
			&& !player.hasVehicle() && !player.isSprinting();
		if (crit) damage *= 1.5F;

		boolean sprintKb = player.isSprinting() && charged;
		boolean sweep = isSword(held) && charged && !crit && !smash && !sprintKb && player.isOnGround();
		double kb = player.getAttributeValue(EntityAttributes.ATTACK_KNOCKBACK) + (sprintKb ? 1.0 : 0.0);

		// ---- health + totem
		boolean wouldDie = dummy.applyDamage(damage);

		// ---- hurt flash, hurt sound, knockback (same numbers vanilla uses)
		dummy.flashHurt();
		play(world, dummy, SoundEvents.ENTITY_PLAYER_HURT, 1.0F);
		dummy.applyKnockback(0.4, player.getX() - dummy.getX(), player.getZ() - dummy.getZ());
		if (kb > 0.0) {
			float yaw = player.getYaw() * 0.017453292F;
			dummy.applyKnockback(kb * 0.5, MathHelper.sin(yaw), -MathHelper.cos(yaw));
		}
		if (smash) { // mace smash shoves the target away a bit more the further you fell
			dummy.applyKnockback(0.3 + Math.min(fall, 10.0) * 0.04, player.getX() - dummy.getX(), player.getZ() - dummy.getZ());
		}
		int combo = dummy.registerHit();

		// ---- attack sounds
		SoundEvent sound;
		if (smash) {
			sound = fall > 5.0 ? SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY
				: dummy.isOnGround() ? SoundEvents.ITEM_MACE_SMASH_GROUND : SoundEvents.ITEM_MACE_SMASH_AIR;
		} else if (crit) {
			sound = SoundEvents.ENTITY_PLAYER_ATTACK_CRIT;
		} else if (sweep) {
			sound = SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP;
		} else {
			sound = charged ? SoundEvents.ENTITY_PLAYER_ATTACK_STRONG : SoundEvents.ENTITY_PLAYER_ATTACK_WEAK;
		}
		play(world, dummy, sound, 1.0F);
		if (sprintKb) play(world, dummy, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F);

		// ---- particles: crit stars, sword sweep, red damage-indicator hearts
		if (crit || smash) player.addCritParticles(dummy);
		if (sweep) {
			double dx = -MathHelper.sin(player.getYaw() * 0.017453292F);
			double dz = MathHelper.cos(player.getYaw() * 0.017453292F);
			world.addParticleClient(ParticleTypes.SWEEP_ATTACK, false, false,
				player.getX() + dx, player.getY() + player.getHeight() * 0.5, player.getZ() + dz, dx, 0.0, dz);
		}
		int hearts = MathHelper.clamp(Math.round(damage * 0.5F), 1, 12);
		for (int i = 0; i < hearts; i++) {
			world.addParticleClient(ParticleTypes.DAMAGE_INDICATOR, false, false,
				dummy.getX() + (FX.nextDouble() - 0.5) * 0.6,
				dummy.getY() + dummy.getHeight() * 0.5 + FX.nextDouble() * 0.4,
				dummy.getZ() + (FX.nextDouble() - 0.5) * 0.6,
				(FX.nextDouble() - 0.5) * 0.2, 0.15 + FX.nextDouble() * 0.15, (FX.nextDouble() - 0.5) * 0.2);
		}

		// ---- floating red damage number
		String number = "-" + String.format(Locale.ROOT, "%.1f", damage) + (crit ? "!" : "");
		int rgb = smash ? 0xFFAA00 : crit ? 0xFF7733 : 0xFF3333;
		DummyHud.add(dummy.getX() + (FX.nextDouble() - 0.5) * 0.8, dummy.getY() + dummy.getHeight() + 0.1,
			dummy.getZ() + (FX.nextDouble() - 0.5) * 0.8, number, rgb);

		// ---- totem pop
		if (wouldDie) {
			dummy.popTotem();
			totemEffects(mc, world, dummy);
			DummyHud.add(dummy.getX(), dummy.getY() + dummy.getHeight() + 0.7, dummy.getZ(),
				"TOTEM POP #" + dummy.getTotemPops(), 0xFFD700);
		}

		// ---- mace boost (single-player only, see DummyBoost)
		String boost = null;
		if (smash && mc.player != null && player == mc.player) boost = DummyBoost.onSmash(mc, mc.player);

		player.resetTicksSinceLastAttack();

		// ---- readout above the hotbar
		StringBuilder sb = new StringBuilder();
		sb.append("\u00a7c-").append(String.format(Locale.ROOT, "%.1f", damage))
			.append(" \u00a77| \u00a7fHP ").append(String.format(Locale.ROOT, "%.1f", dummy.getDummyHealth()))
			.append("/").append(String.format(Locale.ROOT, "%.0f", dummy.getDummyMaxHealth()));
		if (dummy.getDummyAbsorption() > 0.0F) {
			sb.append(" \u00a76+").append(String.format(Locale.ROOT, "%.1f", dummy.getDummyAbsorption()));
		}
		sb.append(" \u00a78| \u00a7fx").append(combo);
		if (wouldDie) sb.append(" \u00a78| \u00a76TOTEM POP #").append(dummy.getTotemPops());
		if (smash) sb.append(" \u00a78| \u00a76SMASH ").append(String.format(Locale.ROOT, "%.1f", fall)).append("m");
		else if (crit) sb.append(" \u00a78| \u00a7eCRIT");
		if (sprintKb) sb.append(" \u00a78| \u00a7aSPRINT KB");
		if (boost != null) sb.append(" \u00a78| \u00a7b").append(boost);
		if (!charged) sb.append(" \u00a78| \u00a7cweak: ").append((int) (cooldown * 100)).append("% charged");
		DummyManager.overlay(mc, sb.toString());
	}

	/** Spear jab: the server does the real hit detection, so do our own ray check against the dummies. */
	public static void jab() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player == null || DummyManager.all().isEmpty()) return;

		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVector().multiply(SPEAR_REACH));
		PracticeDummy best = null;
		double bestDist = Double.MAX_VALUE;
		for (PracticeDummy dummy : DummyManager.all()) {
			Optional<Vec3d> hit = dummy.getBoundingBox().expand(SPEAR_MARGIN).raycast(eye, end);
			if (hit.isPresent()) {
				double dist = eye.squaredDistanceTo(hit.get());
				if (dist < bestDist) {
					bestDist = dist;
					best = dummy;
				}
			}
		}
		if (best != null) hit(player, best);
	}

	/** Totem pop: the real sound, the vanilla particle emitter, plus an instant burst so it's obvious. */
	private static void totemEffects(MinecraftClient mc, ClientWorld world, PracticeDummy dummy) {
		play(world, dummy, SoundEvents.ITEM_TOTEM_USE, 1.0F);
		mc.particleManager.addEmitter(dummy, ParticleTypes.TOTEM_OF_UNDYING, 30);
		for (int i = 0; i < 40; i++) {
			world.addParticleClient(ParticleTypes.TOTEM_OF_UNDYING, false, false,
				dummy.getX() + (FX.nextDouble() - 0.5) * 0.6,
				dummy.getY() + FX.nextDouble() * dummy.getHeight(),
				dummy.getZ() + (FX.nextDouble() - 0.5) * 0.6,
				(FX.nextDouble() - 0.5) * 0.9, FX.nextDouble() * 0.7, (FX.nextDouble() - 0.5) * 0.9);
		}
	}

	private static boolean isSword(ItemStack stack) {
		return !stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).getPath().endsWith("_sword");
	}

	/** Vanilla mace bonus: 4/block for the first 3, 2/block up to 8, then 1/block. */
	private static double smashBonus(double fall) {
		double bonus = 4.0 * Math.min(fall, 3.0);
		if (fall > 3.0) bonus += 2.0 * (Math.min(fall, 8.0) - 3.0);
		if (fall > 8.0) bonus += fall - 8.0;
		return bonus;
	}

	private static void play(ClientWorld world, PracticeDummy at, SoundEvent sound, float volume) {
		world.playSoundClient(at.getX(), at.getY(), at.getZ(), sound, SoundCategory.PLAYERS, volume, 1.0F, false);
	}
}
