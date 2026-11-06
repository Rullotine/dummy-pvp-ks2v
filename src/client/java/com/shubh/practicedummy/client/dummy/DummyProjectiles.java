package com.shubh.practicedummy.client.dummy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Arrows shot by bow-holding dummies. They are simulated here (never real entities), drawn as a crit
 * particle trail, and never hurt you: a hit just plays the sound and flashes the screen.
 */
public final class DummyProjectiles {
	private static final double SPEED = 3.0;
	private static final List<Arrow> ARROWS = new ArrayList<>();
	private static final Random RNG = new Random();

	private static final class Arrow {
		Vec3d pos, vel;
		int age;
	}

	private DummyProjectiles() {}

	public static void clear() {
		ARROWS.clear();
	}

	public static void shoot(PracticeDummy shooter, ClientPlayerEntity target) {
		Vec3d from = shooter.getEyePos().add(0.0, -0.1, 0.0);
		Vec3d center = target.getBoundingBox().getCenter();
		double h0 = Math.hypot(center.x - from.x, center.z - from.z);
		Vec3d to = center.add(target.getVelocity().multiply(h0 / 2.8 * 0.8)); // light lead
		double dx = to.x - from.x, dz = to.z - from.z, dy = to.y - from.y;
		double h = Math.sqrt(dx * dx + dz * dz);
		if (h < 0.01) return;

		double pitch = solvePitch(h, dy);
		double yaw = Math.atan2(dz, dx) + (RNG.nextDouble() - 0.5) * 0.05;
		pitch += (RNG.nextDouble() - 0.5) * 0.05;
		Arrow a = new Arrow();
		a.pos = from;
		a.vel = new Vec3d(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch)).multiply(SPEED);
		ARROWS.add(a);
		ClientWorld w = MinecraftClient.getInstance().world;
		if (w != null) {
			w.playSoundClient(from.x, from.y, from.z, SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0F, 1.0F, false);
		}
	}

	/** Lowest launch angle at which a drag+gravity arrow reaches (h, dy). */
	private static double solvePitch(double h, double dy) {
		for (double deg = -10.0; deg <= 60.0; deg += 0.5) {
			double ang = Math.toRadians(deg);
			double x = 0.0, y = 0.0, vx = Math.cos(ang) * SPEED, vy = Math.sin(ang) * SPEED;
			for (int t = 0; t < 120; t++) {
				x += vx;
				y += vy;
				vx *= 0.99;
				vy = vy * 0.99 - 0.05;
				if (x >= h) {
					if (y >= dy) return ang;
					break;
				}
			}
		}
		return Math.atan2(dy, h);
	}

	public static void tick(MinecraftClient mc) {
		ClientWorld world = mc.world;
		ClientPlayerEntity player = mc.player;
		if (world == null || player == null) {
			ARROWS.clear();
			return;
		}
		Iterator<Arrow> it = ARROWS.iterator();
		while (it.hasNext()) {
			Arrow a = it.next();
			boolean done = false;
			for (int s = 0; s < 3 && !done; s++) {
				Vec3d next = a.pos.add(a.vel.multiply(1.0 / 3.0));
				BlockPos bp = BlockPos.ofFloored(next);
				if (!world.getBlockState(bp).getCollisionShape(world, bp).isEmpty()) {
					done = true;
					break;
				}
				Box box = player.getBoundingBox().expand(0.3);
				if (box.contains(next) || box.raycast(a.pos, next).isPresent()) {
					hitPlayer(mc, world, player, a);
					done = true;
					break;
				}
				a.pos = next;
				world.addParticleClient(ParticleTypes.CRIT, false, false, a.pos.x, a.pos.y, a.pos.z, 0.0, 0.0, 0.0);
			}
			a.vel = a.vel.multiply(0.99).add(0.0, -0.05, 0.0);
			if (done || ++a.age > 200) it.remove();
		}
	}

	private static void hitPlayer(MinecraftClient mc, ClientWorld world, ClientPlayerEntity player, Arrow a) {
		boolean blocked = player.isBlocking() && player.getRotationVector().dotProduct(a.vel.negate()) > 0.0;
		if (blocked) {
			world.playSoundClient(player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_SHIELD_BLOCK,
				SoundCategory.PLAYERS, 1.0F, 1.0F, false);
			DummyManager.overlay(mc, "\u00a7bYou blocked the arrow");
		} else {
			world.playSoundClient(player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ARROW_HIT_PLAYER,
				SoundCategory.PLAYERS, 1.0F, 1.0F, false);
			DummyHud.flash(0xFF0000);
			DummyManager.overlay(mc, "\u00a7cArrow hit you \u00a77(simulated, no damage, ~"
				+ (int) Math.ceil(a.vel.length() * 2.0) + " would have landed)");
		}
	}
}
