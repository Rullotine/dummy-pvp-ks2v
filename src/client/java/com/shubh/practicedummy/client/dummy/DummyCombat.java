package com.shubh.practicedummy.client.dummy;

import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Re-implements the parts of a vanilla attack that matter for practice (damage, cooldown scaling,
 * crits, sprint knockback, sweep, mace smash, Sharpness/Density/Breach, armor, shields, arrows).
 */
public final class DummyCombat {
	private static final double SPEAR_REACH = 4.5;
	private static final double SPEAR_MARGIN = 0.125;
	/** Axes disable a shield for 5 seconds. */
	private static final int SHIELD_DISABLE_TICKS = 100;
	private static final Random FX = new Random();
	private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

	/**
	 * Vanilla's server turns your sprint flag off after a sprint-knockback hit; you have to release and
	 * re-press sprint (w-tap) to get it again. We mimic that.
	 */
	private static boolean sprintUsed;

	private DummyCombat() {}

	private record Result(boolean died, int combo) {}

	public static String roman(int n) {
		return ROMAN[MathHelper.clamp(n, 0, 5)];
	}

	public static int enchantLevel(ItemStack stack, RegistryKey<Enchantment> key) {
		if (stack == null || stack.isEmpty()) return 0;
		for (var entry : stack.getEnchantments().getEnchantmentEntries()) {
			if (entry.getKey().getKey().filter(k -> k.equals(key)).isPresent()) return entry.getIntValue();
		}
		return 0;
	}

	/** Called every client tick. */
	public static void tick(MinecraftClient mc) {
		ClientWorld world = mc.world;
		ClientPlayerEntity player = mc.player;
		if (world == null || player == null) return;
		if (!player.isSprinting()) sprintUsed = false;
		if (DummyManager.all().isEmpty()) return;

		for (PracticeDummy dummy : DummyManager.all()) {
			Box area = dummy.getBoundingBox().expand(6.0);
			for (ArrowEntity arrow : world.getEntitiesByClass(ArrowEntity.class, area, a -> a.getOwner() == player)) {
				Vec3d vel = arrow.getVelocity();
				if (arrow.isRemoved() || vel.lengthSquared() < 0.25) continue;
				Vec3d to = arrow.getPos();
				Vec3d from = to.subtract(vel);
				Box box = dummy.getBoundingBox().expand(0.3);
				if (box.contains(to) || box.raycast(from, to).isPresent()) {
					float dmg = (float) Math.ceil(vel.length() * 2.0);
					boolean crit = arrow.isCritical();
					if (crit) dmg += FX.nextInt((int) dmg / 2 + 2);
					arrow.discard();
					rangedHit(mc, world, player, dummy, dmg, crit, vel);
				}
			}
		}
	}

	private static void rangedHit(MinecraftClient mc, ClientWorld world, PlayerEntity player, PracticeDummy dummy,
		float raw, boolean crit, Vec3d vel) {
		if (!dummy.canBeHit()) return;
		if (dummy.isShieldUp() && dummy.isFacing(player.getX(), player.getZ())) {
			play(world, dummy, SoundEvents.ITEM_SHIELD_BLOCK, 1.0F);
			dummy.noteBlocked();
			DummyManager.setFocus(dummy);
			DummyManager.overlay(mc, "\u00a7bArrow blocked by shield");
			return;
		}
		float damage = DummySettings.armorCalc ? DummySettings.armor.reduce(raw, 0) : raw;
		Result r = damageDummy(mc, world, dummy, damage, crit, false);
		dummy.applyKnockback(0.25, -vel.x, -vel.z);
		playAt(world, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ARROW_HIT_PLAYER, 0.8F, 1.0F);
		effects(world, dummy, damage, crit, false);
		afterHit(mc, world, dummy, r);
		DummyManager.overlay(mc, "\u00a7c-" + fmt(damage) + " \u00a77arrow" + (crit ? " \u00a7e(crit)" : "")
			+ " \u00a78| \u00a7fHP " + fmt(dummy.getDummyHealth()) + "/" + (int) dummy.getDummyMaxHealth());
	}

	public static void hit(PlayerEntity player, PracticeDummy dummy) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld world = mc.world;
		if (world == null) return;

		ItemStack held = player.getMainHandStack();
		float cooldown = player.getAttackCooldownProgress(0.5F);
		boolean charged = cooldown > 0.9F;
		double fall = player.fallDistance;
		boolean mace = held.isOf(Items.MACE);
		boolean smash = mace && fall > 1.5 && !player.isGliding();

		// ---- shield: axe disables it, everything else is blocked
		if (dummy.isShieldUp() && dummy.isFacing(player.getX(), player.getZ())) {
			DummyManager.setFocus(dummy);
			if (isAxe(held)) {
				dummy.disableShield(SHIELD_DISABLE_TICKS);
				play(world, dummy, SoundEvents.ITEM_SHIELD_BREAK, 1.0F);
				DummyHud.add(dummy.getX(), dummy.getY() + dummy.getHeight() + 0.1, dummy.getZ(), "SHIELD DOWN", 0x55FFFF);
				DummyManager.overlay(mc, "\u00a7bShield disabled \u00a77| swap to mace and smash within \u00a7f5.0s");
			} else {
				play(world, dummy, SoundEvents.ITEM_SHIELD_BLOCK, 1.0F);
				dummy.noteBlocked();
				DummyManager.overlay(mc, mace
					? "\u00a7cBlocked \u00a77| a mace doesn't break shields, axe it first"
					: "\u00a77Blocked by shield");
			}
			player.resetTicksSinceLastAttack();
			return;
		}

		if (!dummy.canBeHit()) {
			player.resetTicksSinceLastAttack();
			return;
		}

		boolean stunned = dummy.isShieldDisabled();
		boolean serverSprint = player.isSprinting() && !sprintUsed;

		double base = player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE) + (smash ? smashBonus(fall) : 0.0);
		float damage = (float) base * (0.2F + cooldown * cooldown * 0.8F);

		boolean crit = charged && !smash && fall > 0.0 && !player.isOnGround() && !player.isClimbing()
			&& !player.isTouchingWater() && !player.hasStatusEffect(StatusEffects.BLINDNESS)
			&& !player.hasVehicle() && !serverSprint;
		if (crit) damage *= 1.5F;

		// enchantments (added after the crit multiplier, scaled by attack charge, like vanilla)
		int sharp = enchantLevel(held, Enchantments.SHARPNESS);
		int density = enchantLevel(held, Enchantments.DENSITY);
		int breach = mace ? enchantLevel(held, Enchantments.BREACH) : 0;
		float bonus = 0.0F;
		if (sharp > 0) bonus += 0.5F * sharp + 0.5F;
		if (smash && density > 0) bonus += 0.5F * density * (float) fall;
		damage += bonus * cooldown;

		float raw = damage;
		if (DummySettings.armorCalc) damage = DummySettings.armor.reduce(damage, breach);

		boolean sprintKb = serverSprint && charged;
		boolean sweep = isSword(held) && charged && !crit && !smash && !sprintKb && player.isOnGround();
		double kb = player.getAttributeValue(EntityAttributes.ATTACK_KNOCKBACK) + (sprintKb ? 1.0 : 0.0);

		Result r = damageDummy(mc, world, dummy, damage, crit, smash);

		dummy.applyKnockback(0.4, player.getX() - dummy.getX(), player.getZ() - dummy.getZ());
		if (kb > 0.0) {
			float yaw = player.getYaw() * 0.017453292F;
			dummy.applyKnockback(kb * 0.5, MathHelper.sin(yaw), -MathHelper.cos(yaw));
		}
		if (smash) {
			dummy.applyKnockback(0.3 + Math.min(fall, 10.0) * 0.04, player.getX() - dummy.getX(), player.getZ() - dummy.getZ());
		}
		if (sprintKb) sprintUsed = true;

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

		if (crit || smash) player.addCritParticles(dummy);
		if (sweep) {
			double dx = -MathHelper.sin(player.getYaw() * 0.017453292F);
			double dz = MathHelper.cos(player.getYaw() * 0.017453292F);
			world.addParticleClient(ParticleTypes.SWEEP_ATTACK, false, false,
				player.getX() + dx, player.getY() + player.getHeight() * 0.5, player.getZ() + dz, dx, 0.0, dz);
		}
		effects(world, dummy, damage, crit, smash);
		afterHit(mc, world, dummy, r);

		String boost = null;
		String slam = null;
		if (smash && stunned) {
			int ticks = dummy.getTicksSinceShieldDisabled();
			dummy.recordStunSlam(ticks);
			slam = "STUN SLAM " + String.format(Locale.ROOT, "%.2f", ticks / 20.0F) + "s";
			DummyHud.add(dummy.getX(), dummy.getY() + dummy.getHeight() + 1.2, dummy.getZ(), "STUN SLAM!", 0x55FFFF);
			DummyHud.popBanner("STUN SLAM!", String.format(Locale.ROOT, "%.2fs after the axe", ticks / 20.0F), 0x55FFFF);
		}
		if (smash && mc.player != null && player == mc.player) boost = DummyBoost.onSmash(mc, mc.player);

		player.resetTicksSinceLastAttack();

		StringBuilder sb = new StringBuilder();
		sb.append("\u00a7c-").append(fmt(damage));
		if (DummySettings.armorCalc && raw - damage > 0.05F) sb.append(" \u00a78(").append(fmt(raw)).append(" raw)");
		sb.append(" \u00a77| \u00a7fHP ").append(fmt(dummy.getDummyHealth()))
			.append("/").append(String.format(Locale.ROOT, "%.0f", dummy.getDummyMaxHealth()));
		if (dummy.getDummyAbsorption() > 0.0F) sb.append(" \u00a76+").append(fmt(dummy.getDummyAbsorption()));
		sb.append(" \u00a78| \u00a7fx").append(r.combo());
		if (r.died()) sb.append(" \u00a78| \u00a76TOTEM POP #").append(dummy.getTotemPops());
		if (smash) sb.append(" \u00a78| \u00a76SMASH ").append(fmt((float) fall)).append("m");
		else if (crit) sb.append(" \u00a78| \u00a7eCRIT");
		if (breach > 0) sb.append(" \u00a78| \u00a7dBREACH ").append(roman(breach));
		if (sharp > 0) sb.append(" \u00a78| \u00a7aSharp ").append(roman(sharp));
		if (smash && density > 0) sb.append(" \u00a78| \u00a7aDensity ").append(roman(density));
		if (sprintKb) sb.append(" \u00a78| \u00a7aSPRINT KB");
		if (slam != null) sb.append(" \u00a78| \u00a7b").append(slam);
		if (boost != null) sb.append(" \u00a78| \u00a7b").append(boost);
		if (!charged) sb.append(" \u00a78| \u00a7cweak: ").append((int) (cooldown * 100)).append("% charged");
		DummyManager.overlay(mc, sb.toString());
	}

	/** Spear jab: do our own ray check against the dummies. */
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

	// ---------- shared pieces ----------
	private static Result damageDummy(MinecraftClient mc, ClientWorld world, PracticeDummy dummy, float damage,
		boolean crit, boolean smash) {
		boolean died = dummy.applyDamage(damage);
		dummy.flashHurt();
		play(world, dummy, SoundEvents.ENTITY_PLAYER_HURT, 1.0F);
		int combo = dummy.registerHit();
		dummy.recordHit(damage, crit, smash);
		DummyManager.setFocus(dummy);
		return new Result(died, combo);
	}

	private static void effects(ClientWorld world, PracticeDummy dummy, float damage, boolean crit, boolean smash) {
		int hearts = MathHelper.clamp(Math.round(damage * 0.5F), 1, 12);
		for (int i = 0; i < hearts; i++) {
			world.addParticleClient(ParticleTypes.DAMAGE_INDICATOR, false, false,
				dummy.getX() + (FX.nextDouble() - 0.5) * 0.6,
				dummy.getY() + dummy.getHeight() * 0.5 + FX.nextDouble() * 0.4,
				dummy.getZ() + (FX.nextDouble() - 0.5) * 0.6,
				(FX.nextDouble() - 0.5) * 0.2, 0.15 + FX.nextDouble() * 0.15, (FX.nextDouble() - 0.5) * 0.2);
		}
		String number = "-" + fmt(damage) + (crit ? "!" : "");
		int rgb = smash ? 0xFFAA00 : crit ? 0xFF7733 : 0xFF3333;
		DummyHud.add(dummy.getX() + (FX.nextDouble() - 0.5) * 0.8, dummy.getY() + dummy.getHeight() + 0.1,
			dummy.getZ() + (FX.nextDouble() - 0.5) * 0.8, number, rgb);
	}

	private static void afterHit(MinecraftClient mc, ClientWorld world, PracticeDummy dummy, Result r) {
		if (!r.died()) return;
		dummy.popTotem();
		totemEffects(mc, world, dummy);
		DummyHud.add(dummy.getX(), dummy.getY() + dummy.getHeight() + 0.7, dummy.getZ(),
			"TOTEM POP #" + dummy.getTotemPops(), 0xFFD700);
		DummyHud.popBanner("TOTEM POP #" + dummy.getTotemPops(),
			String.format(Locale.ROOT, "%.2fs / %d hits", dummy.getLastPopSeconds(), dummy.getLastPopHits()), 0xFFD700);
	}

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
		// golden ring + rising sparkle column
		for (int i = 0; i < 24; i++) {
			double a = i / 24.0 * Math.PI * 2.0;
			world.addParticleClient(ParticleTypes.END_ROD, false, false,
				dummy.getX() + Math.cos(a) * 0.5, dummy.getY() + 0.2, dummy.getZ() + Math.sin(a) * 0.5,
				Math.cos(a) * 0.12, 0.08, Math.sin(a) * 0.12);
		}
		for (int i = 0; i < 16; i++) {
			world.addParticleClient(ParticleTypes.FIREWORK, false, false,
				dummy.getX(), dummy.getY() + 0.5 + i * 0.12, dummy.getZ(), 0.0, 0.15, 0.0);
		}
	}

	private static boolean isSword(ItemStack stack) {
		return !stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).getPath().endsWith("_sword");
	}

	private static boolean isAxe(ItemStack stack) {
		return !stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).getPath().endsWith("_axe");
	}

	/** Vanilla mace bonus: 4/block for the first 3, 2/block up to 8, then 1/block. */
	public static double smashBonus(double fall) {
		double bonus = 4.0 * Math.min(fall, 3.0);
		if (fall > 3.0) bonus += 2.0 * (Math.min(fall, 8.0) - 3.0);
		if (fall > 8.0) bonus += fall - 8.0;
		return bonus;
	}

	static String fmt(float v) {
		return String.format(Locale.ROOT, "%.1f", v);
	}

	static void play(ClientWorld world, PracticeDummy at, SoundEvent sound, float volume) {
		playAt(world, at.getX(), at.getY(), at.getZ(), sound, volume, 1.0F);
	}

	static void play(ClientWorld world, PracticeDummy at, RegistryEntry<SoundEvent> sound, float volume) {
		playAt(world, at.getX(), at.getY(), at.getZ(), sound.value(), volume, 1.0F);
	}

	/** Some sounds are SoundEvent and some are registry entries in 1.21.11; these overloads accept both. */
	static void playAt(ClientWorld world, double x, double y, double z, SoundEvent sound, float volume, float pitch) {
		world.playSoundClient(x, y, z, sound, SoundCategory.PLAYERS, volume, pitch, false);
	}

	static void playAt(ClientWorld world, double x, double y, double z, RegistryEntry<SoundEvent> sound, float volume, float pitch) {
		world.playSoundClient(x, y, z, sound.value(), SoundCategory.PLAYERS, volume, pitch, false);
	}
}
