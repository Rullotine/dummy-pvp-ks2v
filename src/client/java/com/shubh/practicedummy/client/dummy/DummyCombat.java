package com.shubh.practicedummy.client.dummy;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
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
 * Re-implements the parts of a vanilla attack that matter for practice (damage, cooldown scaling,
 * crits, sprint knockback, sweep, mace smash + Density/Breach, Sharpness, armor + Protection,
 * shield blocking / axe stuns, arrows) locally, because the server will never process a hit on an
 * entity it doesn't know about.
 */
public final class DummyCombat {
	/** Approximate spear jab reach in blocks. Tweak if it feels off. */
	private static final double SPEAR_REACH = 4.5;
	private static final double SPEAR_MARGIN = 0.125;
	private static final Random FX = new Random();

	/** Everything the shared damage routine needs to know about one hit. */
	private static final class Hit {
		float raw;
		int breach;
		int windLevel;
		boolean melee = true;
		boolean crit, smash, sprintKb, sweep, charged, axe;
		boolean quiet; // no readout (used for sweep splash)
		float cooldown = 1.0F;
		double fall;
		String tag = "";
		Runnable knock = () -> { };
	}

	private DummyCombat() {}

	// ------------------------------------------------------------------ melee
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
		Enchants real = Enchants.of(held);
		int breach = Math.max(real.breach, DummySettings.simBreach);
		int density = Math.max(real.density, DummySettings.simDensity);
		int sharpness = Math.max(real.sharpness, DummySettings.simSharpness);
		int windLevel = real.windBurst > 0 ? real.windBurst : DummySettings.windBurst;

		boolean smash = held.isOf(Items.MACE) && fall > 1.5 && !player.isGliding();
		double bonus = smash ? smashBonus(fall) + density * 0.5 * fall : 0.0;
		double attack = player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
		float physical = (float) (attack + bonus) * (0.2F + cooldown * cooldown * 0.8F);

		boolean crit = charged && !smash && fall > 0.0 && !player.isOnGround() && !player.isClimbing()
			&& !player.isTouchingWater() && !player.hasStatusEffect(StatusEffects.BLINDNESS)
			&& !player.hasVehicle() && !player.isSprinting();
		if (crit) physical *= 1.5F;
		float enchant = sharpness > 0 ? (0.5F * sharpness + 0.5F) * cooldown : 0.0F;

		boolean sprintKb = player.isSprinting() && charged;
		boolean sword = isItem(held, "_sword");
		boolean sweep = sword && charged && !crit && !smash && !sprintKb && player.isOnGround();
		double kb = player.getAttributeValue(EntityAttributes.ATTACK_KNOCKBACK) + real.knockback + (sprintKb ? 1.0 : 0.0);

		Hit h = new Hit();
		h.raw = physical + enchant;
		h.breach = breach;
		h.windLevel = windLevel;
		h.crit = crit;
		h.smash = smash;
		h.sprintKb = sprintKb;
		h.sweep = sweep;
		h.charged = charged;
		h.axe = isItem(held, "_axe");
		h.cooldown = cooldown;
		h.fall = fall;
		final double knockback = kb;
		final boolean doSmash = smash;
		final double fallNow = fall;
		h.knock = () -> {
			dummy.applyKnockback(0.4, player.getX() - dummy.getX(), player.getZ() - dummy.getZ());
			if (knockback > 0.0) {
				float yaw = player.getYaw() * 0.017453292F;
				dummy.applyKnockback(knockback * 0.5, MathHelper.sin(yaw), -MathHelper.cos(yaw));
			}
			if (doSmash) { // mace smash shoves the target away more the further you fell
				dummy.applyKnockback(0.3 + Math.min(fallNow, 10.0) * 0.04,
					player.getX() - dummy.getX(), player.getZ() - dummy.getZ());
			}
		};

		boolean landed = land(mc, world, player, dummy, h);

		// sweeping edge splash on any other dummies standing next to the one you hit
		if (landed && sweep) {
			Box area = dummy.getBoundingBox().expand(1.0, 0.25, 1.0);
			for (PracticeDummy other : new ArrayList<>(DummyManager.all())) {
				if (other == dummy || !other.canBeHit()) continue;
				if (!other.getBoundingBox().intersects(area)) continue;
				if (player.squaredDistanceTo(other.getX(), other.getY(), other.getZ()) >= 9.0) continue;
				Hit s = new Hit();
				s.raw = 1.0F;
				s.quiet = true;
				s.tag = "SWEEP";
				s.knock = () -> other.applyKnockback(0.4, player.getX() - other.getX(), player.getZ() - other.getZ());
				land(mc, world, player, other, s);
			}
		}

		if (landed && sweep) {
			double dx = -MathHelper.sin(player.getYaw() * 0.017453292F);
			double dz = MathHelper.cos(player.getYaw() * 0.017453292F);
			world.addParticleClient(ParticleTypes.SWEEP_ATTACK, false, false,
				player.getX() + dx, player.getY() + player.getHeight() * 0.5, player.getZ() + dz, dx, 0.0, dz);
		}
		player.resetTicksSinceLastAttack();
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

	// ------------------------------------------------------------ spear charge
	/** Approximation of a spear charge (hold right-click and run into it): damage grows with your speed. */
	public static void chargeCheck(MinecraftClient mc) {
		ClientPlayerEntity player = mc.player;
		ClientWorld world = mc.world;
		if (player == null || world == null || DummyManager.all().isEmpty()) return;
		if (!player.isUsingItem() || !isItem(player.getActiveItem(), "_spear")) return;

		Vec3d v = player.getVelocity();
		double speed = Math.hypot(v.x, v.z);
		if (speed < 0.18) return;

		Vec3d look = player.getRotationVector();
		double lookLen = Math.hypot(look.x, look.z);
		if (lookLen < 0.01) return;
		for (PracticeDummy dummy : DummyManager.all()) {
			if (!dummy.canBeHit()) continue;
			double dx = dummy.getX() - player.getX();
			double dz = dummy.getZ() - player.getZ();
			double dist = Math.hypot(dx, dz);
			if (dist > 3.0 || dist < 0.01) continue;
			double facing = (dx * look.x + dz * look.z) / (dist * lookLen);
			if (facing < 0.6 || Math.abs(dummy.getY() - player.getY()) > 2.0) continue;

			Hit h = new Hit();
			h.raw = (float) (player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE) + speed * 30.0);
			h.tag = String.format(Locale.ROOT, "SPEAR CHARGE %.1f b/s", speed * 20.0);
			h.charged = true;
			h.knock = () -> dummy.applyKnockback(0.4 + speed * 2.0, player.getX() - dummy.getX(), player.getZ() - dummy.getZ());
			land(mc, world, player, dummy, h);
			break;
		}
	}

	// ------------------------------------------------------------- projectiles
	public static void projectile(MinecraftClient mc, ClientPlayerEntity player, PracticeDummy dummy, PersistentProjectileEntity arrow) {
		ClientWorld world = mc.world;
		if (world == null) return;
		Vec3d vel = arrow.getVelocity();
		double speed = vel.length();
		boolean trident = arrow instanceof TridentEntity;
		int damage = (int) Math.ceil(speed * (trident ? 8.0 : 2.0));
		if (arrow.isCritical()) damage += FX.nextInt(damage / 2 + 2);

		Hit h = new Hit();
		h.melee = false;
		h.raw = damage;
		h.tag = trident ? "TRIDENT" : "ARROW";
		h.knock = () -> dummy.applyKnockback(0.25, -vel.x, -vel.z);
		boolean landed = land(mc, world, player, dummy, h);
		if (landed) { // the classic "ding" you get for hitting a player
			world.playSoundClient(player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ARROW_HIT_PLAYER,
				SoundCategory.PLAYERS, 0.18F, 0.45F, false);
		}
	}

	// ------------------------------------------------------------ shared damage
	private static boolean land(MinecraftClient mc, ClientWorld world, PlayerEntity attacker, PracticeDummy dummy, Hit h) {
		if (!dummy.canBeHit()) return false;

		// ---- shield: blocks everything, an axe hit disables it ("stun")
		if (dummy.isShieldUp()) {
			dummy.blockedHit();
			if (h.axe && h.charged) {
				dummy.stun(DummySettings.stunTicks);
				DummyStats.shieldBreaks++;
				play(world, dummy, SoundEvents.ITEM_SHIELD_BREAK, 1.0F, 1.0F);
				DummyHud.add(dummy.getX(), dummy.getY() + dummy.getHeight() + 0.3, dummy.getZ(), "STUNNED!", 0x55FFFF);
				if (!h.quiet) DummyManager.overlay(mc, "\u00a7bSHIELD DISABLED \u00a77- slam it now! ("
					+ String.format(Locale.ROOT, "%.1f", DummySettings.stunTicks / 20.0F) + "s)");
			} else {
				play(world, dummy, SoundEvents.ITEM_SHIELD_BLOCK, 1.0F, 1.0F);
				DummyHud.add(dummy.getX(), dummy.getY() + dummy.getHeight() + 0.3, dummy.getZ(), "BLOCKED", 0x9999AA);
				if (!h.quiet) DummyManager.overlay(mc, "\u00a77Blocked by the shield \u00a78- hit it with an axe to stun it");
			}
			return false;
		}

		// ---- damage reduction: armor (Breach cuts through it), then Protection
		float afterArmor = armorReduce(h.raw, dummy.getArmorPoints(), dummy.getArmorToughness(), h.breach);
		float epf = Math.min(20.0F, DummySettings.protection * 4.0F);
		float damage = afterArmor * (1.0F - epf / 25.0F);

		boolean wouldDie = dummy.applyDamage(damage);
		dummy.flashHurt();
		play(world, dummy, SoundEvents.ENTITY_PLAYER_HURT, 1.0F, 1.0F);
		h.knock.run();
		int combo = dummy.registerHit();
		DummyStats.recordHit(damage, combo);

		// ---- stun slam bookkeeping
		boolean stunSlam = h.smash && dummy.isStunned();
		if (stunSlam) {
			DummyStats.recordStunSlam(dummy.getStunAge());
			DummyHud.add(dummy.getX(), dummy.getY() + dummy.getHeight() + 0.9, dummy.getZ(), "STUN SLAM!", 0x55FFFF);
		}

		// ---- mace blast on the dummy + player boost
		String boost = null;
		if (h.smash) {
			dummy.launch(0.35 + 0.2 * h.windLevel);
			ClientPlayerEntity self = mc.player;
			if (h.melee && self != null && attacker == self) boost = DummyBoost.onSmash(mc, self, h.windLevel, h.fall);
		}

		// ---- sounds
		SoundEvent sound = null;
		if (h.melee) {
			if (h.smash) {
				sound = h.fall > 5.0 ? SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY
					: dummy.isOnGround() ? SoundEvents.ITEM_MACE_SMASH_GROUND : SoundEvents.ITEM_MACE_SMASH_AIR;
			} else if (h.crit) {
				sound = SoundEvents.ENTITY_PLAYER_ATTACK_CRIT;
			} else if (h.sweep) {
				sound = SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP;
			} else if (h.tag.isEmpty()) {
				sound = h.charged ? SoundEvents.ENTITY_PLAYER_ATTACK_STRONG : SoundEvents.ENTITY_PLAYER_ATTACK_WEAK;
			} else {
				sound = SoundEvents.ENTITY_PLAYER_ATTACK_STRONG;
			}
		}
		if (sound != null) play(world, dummy, sound, 1.0F, 1.0F);
		if (h.sprintKb) play(world, dummy, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0F, 1.0F);
		if (combo >= 3) { // rising "ping" for combos
			float pitch = 0.8F + Math.min(combo, 12) * 0.06F;
			play(world, dummy, SoundEvents.BLOCK_NOTE_BLOCK_PLING, 0.35F, pitch);
		}

		// ---- particles: crit stars, red damage hearts, enchant sparkle, mace gust
		if ((h.crit || h.smash) && h.melee) attacker.addCritParticles(dummy);
		int hearts = MathHelper.clamp(Math.round(damage * 0.5F), 1, 12);
		for (int i = 0; i < hearts; i++) {
			world.addParticleClient(ParticleTypes.DAMAGE_INDICATOR, false, false,
				dummy.getX() + (FX.nextDouble() - 0.5) * 0.6,
				dummy.getY() + dummy.getHeight() * 0.5 + FX.nextDouble() * 0.4,
				dummy.getZ() + (FX.nextDouble() - 0.5) * 0.6,
				(FX.nextDouble() - 0.5) * 0.2, 0.15 + FX.nextDouble() * 0.15, (FX.nextDouble() - 0.5) * 0.2);
		}
		if (h.breach > 0 || h.smash || h.crit) {
			for (int i = 0; i < 6; i++) {
				world.addParticleClient(ParticleTypes.ENCHANTED_HIT, false, false,
					dummy.getX() + (FX.nextDouble() - 0.5) * 0.7,
					dummy.getY() + dummy.getHeight() * 0.6 + (FX.nextDouble() - 0.5) * 0.6,
					dummy.getZ() + (FX.nextDouble() - 0.5) * 0.7,
					(FX.nextDouble() - 0.5) * 0.4, FX.nextDouble() * 0.3, (FX.nextDouble() - 0.5) * 0.4);
			}
		}
		if (h.smash && h.fall > 3.0) {
			world.addParticleClient(h.fall > 8.0 ? ParticleTypes.GUST_EMITTER_LARGE : ParticleTypes.GUST_EMITTER_SMALL,
				false, false, dummy.getX(), dummy.getY() + 0.1, dummy.getZ(), 0.0, 0.0, 0.0);
		}

		// ---- floating red damage number
		String number = "-" + String.format(Locale.ROOT, "%.1f", damage) + (h.crit ? "!" : "");
		int rgb = h.smash ? 0xFFAA00 : h.crit ? 0xFF7733 : 0xFF3333;
		DummyHud.add(dummy.getX() + (FX.nextDouble() - 0.5) * 0.8, dummy.getY() + dummy.getHeight() + 0.1,
			dummy.getZ() + (FX.nextDouble() - 0.5) * 0.8, number, rgb);

		// ---- totem pop
		if (wouldDie) {
			dummy.popTotem();
			DummyStats.totemPops++;
			totemEffects(mc, world, dummy);
			DummyHud.add(dummy.getX(), dummy.getY() + dummy.getHeight() + 0.7, dummy.getZ(),
				"TOTEM POP #" + dummy.getTotemPops(), 0xFFD700);
		}

		// ---- readout above the hotbar
		if (!h.quiet) {
			StringBuilder sb = new StringBuilder();
			sb.append("\u00a7c-").append(String.format(Locale.ROOT, "%.1f", damage))
				.append(" \u00a77| \u00a7fHP ").append(String.format(Locale.ROOT, "%.1f", dummy.getDummyHealth()))
				.append("/").append(String.format(Locale.ROOT, "%.0f", dummy.getDummyMaxHealth()));
			if (dummy.getDummyAbsorption() > 0.0F) {
				sb.append(" \u00a76+").append(String.format(Locale.ROOT, "%.1f", dummy.getDummyAbsorption()));
			}
			sb.append(" \u00a78| \u00a7fx").append(combo);
			if (!h.tag.isEmpty()) sb.append(" \u00a78| \u00a7d").append(h.tag);
			if (wouldDie) sb.append(" \u00a78| \u00a76TOTEM POP #").append(dummy.getTotemPops());
			if (stunSlam) sb.append(" \u00a78| \u00a7bSTUN SLAM ").append(String.format(Locale.ROOT, "%.2fs", dummy.getStunAge() / 20.0F));
			if (h.smash) sb.append(" \u00a78| \u00a76SMASH ").append(String.format(Locale.ROOT, "%.1f", h.fall)).append("m");
			else if (h.crit) sb.append(" \u00a78| \u00a7eCRIT");
			if (h.breach > 0) sb.append(" \u00a78| \u00a75BREACH ").append(h.breach);
			if (h.sprintKb) sb.append(" \u00a78| \u00a7aSPRINT KB");
			if (boost != null) sb.append(" \u00a78| \u00a7b").append(boost);
			if (h.melee && !h.charged && h.tag.isEmpty()) {
				sb.append(" \u00a78| \u00a7cweak: ").append((int) (h.cooldown * 100)).append("% charged");
			}
			DummyManager.overlay(mc, sb.toString());
		}
		return true;
	}

	/** Vanilla armor formula, with Breach shaving 15% per level off the armor's effect. */
	static float armorReduce(float damage, float armor, float toughness, int breach) {
		float f = 2.0F + toughness / 4.0F;
		float g = MathHelper.clamp(armor - damage / f, armor * 0.2F, 20.0F);
		float reduction = g / 25.0F * Math.max(0.0F, 1.0F - 0.15F * breach);
		return damage * (1.0F - reduction);
	}

	/** Totem pop: the real sound, the vanilla particle emitter, plus an instant burst so it's obvious. */
	private static void totemEffects(MinecraftClient mc, ClientWorld world, PracticeDummy dummy) {
		play(world, dummy, SoundEvents.ITEM_TOTEM_USE, 1.0F, 1.0F);
		mc.particleManager.addEmitter(dummy, ParticleTypes.TOTEM_OF_UNDYING, 30);
		for (int i = 0; i < 40; i++) {
			world.addParticleClient(ParticleTypes.TOTEM_OF_UNDYING, false, false,
				dummy.getX() + (FX.nextDouble() - 0.5) * 0.6,
				dummy.getY() + FX.nextDouble() * dummy.getHeight(),
				dummy.getZ() + (FX.nextDouble() - 0.5) * 0.6,
				(FX.nextDouble() - 0.5) * 0.9, FX.nextDouble() * 0.7, (FX.nextDouble() - 0.5) * 0.9);
		}
		for (int i = 0; i < 16; i++) {
			world.addParticleClient(ParticleTypes.FIREWORK, false, false,
				dummy.getX(), dummy.getY() + dummy.getHeight() * 0.6, dummy.getZ(),
				(FX.nextDouble() - 0.5) * 0.6, FX.nextDouble() * 0.5, (FX.nextDouble() - 0.5) * 0.6);
		}
	}

	private static boolean isItem(ItemStack stack, String pathSuffix) {
		return stack != null && !stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).getPath().endsWith(pathSuffix);
	}

	/** Vanilla mace bonus: 4/block for the first 3, 2/block up to 8, then 1/block. */
	private static double smashBonus(double fall) {
		double bonus = 4.0 * Math.min(fall, 3.0);
		if (fall > 3.0) bonus += 2.0 * (Math.min(fall, 8.0) - 3.0);
		if (fall > 8.0) bonus += fall - 8.0;
		return bonus;
	}

	private static void play(ClientWorld world, PracticeDummy at, SoundEvent sound, float volume, float pitch) {
		world.playSoundClient(at.getX(), at.getY(), at.getZ(), sound, SoundCategory.PLAYERS, volume, pitch, false);
	}
}
