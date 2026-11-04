package com.shubh.practicedummy.client.dummy;

import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Re-implements the parts of a vanilla player attack that matter for practice (damage number,
 * attack-cooldown scaling, crits, sprint knockback, mace smash bonus, hit feedback) locally,
 * because the server will never process a hit on an entity it doesn't know about.
 */
public final class DummyCombat {
	/** Approximate spear jab reach in blocks. Tweak if it feels off. */
	private static final double SPEAR_REACH = 4.5;
	private static final double SPEAR_MARGIN = 0.125;

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

		boolean smash = player.getMainHandStack().isOf(Items.MACE) && fall > 1.5 && !player.isGliding();
		double base = player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE) + (smash ? smashBonus(fall) : 0.0);
		float damage = (float) base * (0.2F + cooldown * cooldown * 0.8F);

		boolean crit = charged && !smash && fall > 0.0 && !player.isOnGround() && !player.isClimbing()
			&& !player.isTouchingWater() && !player.hasStatusEffect(StatusEffects.BLINDNESS)
			&& !player.hasVehicle() && !player.isSprinting();
		if (crit) damage *= 1.5F;

		boolean sprintKb = player.isSprinting() && charged;
		double kb = player.getAttributeValue(EntityAttributes.ATTACK_KNOCKBACK) + (sprintKb ? 1.0 : 0.0);

		// --- hit feedback on the dummy (the same things the client does when the server says "hurt")
		dummy.handleStatus((byte) 2); // hurt flash + hurt sound + head tilt
		dummy.takeKnockback(0.4, player.getX() - dummy.getX(), player.getZ() - dummy.getZ());
		if (kb > 0.0) {
			float yaw = player.getYaw() * 0.017453292F;
			dummy.takeKnockback(kb * 0.5, MathHelper.sin(yaw), -MathHelper.cos(yaw));
		}
		int combo = dummy.registerHit();

		// --- sounds / particles
		SoundEvent sound;
		if (smash) {
			sound = fall > 5.0 ? SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY
				: dummy.isOnGround() ? SoundEvents.ITEM_MACE_SMASH_GROUND : SoundEvents.ITEM_MACE_SMASH_AIR;
		} else if (crit) {
			sound = SoundEvents.ENTITY_PLAYER_ATTACK_CRIT;
		} else {
			sound = charged ? SoundEvents.ENTITY_PLAYER_ATTACK_STRONG : SoundEvents.ENTITY_PLAYER_ATTACK_WEAK;
		}
		play(world, dummy, sound);
		if (sprintKb) play(world, dummy, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK);
		if (crit || smash) player.addCritParticles(dummy);

		player.resetTicksSinceLastAttack();

		// --- readout
		StringBuilder sb = new StringBuilder();
		sb.append("\u00a7c").append(String.format(Locale.ROOT, "%.1f", damage)).append(" dmg")
			.append(" \u00a77(").append(String.format(Locale.ROOT, "%.2f", damage / 2.0F)).append(" hearts)")
			.append(" \u00a78| \u00a7fcombo x").append(combo);
		if (smash) sb.append(" \u00a78| \u00a76SMASH ").append(String.format(Locale.ROOT, "%.1f", fall)).append(" blocks");
		else if (crit) sb.append(" \u00a78| \u00a7eCRIT");
		if (sprintKb) sb.append(" \u00a78| \u00a7aSPRINT KB");
		if (!charged) sb.append(" \u00a78| \u00a7cweak hit, only ").append((int) (cooldown * 100)).append("% charged");
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

	/** Vanilla mace bonus: 4/block for the first 3, 2/block up to 8, then 1/block. */
	private static double smashBonus(double fall) {
		double bonus = 4.0 * Math.min(fall, 3.0);
		if (fall > 3.0) bonus += 2.0 * (Math.min(fall, 8.0) - 3.0);
		if (fall > 8.0) bonus += fall - 8.0;
		return bonus;
	}

	private static void play(ClientWorld world, PracticeDummy at, SoundEvent sound) {
		world.playSoundClient(at.getX(), at.getY(), at.getZ(), sound, SoundCategory.PLAYERS, 1.0F, 1.0F, false);
	}
}
