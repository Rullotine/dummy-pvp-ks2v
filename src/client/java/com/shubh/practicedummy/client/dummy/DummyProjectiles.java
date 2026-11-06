package com.shubh.practicedummy.client.dummy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Arrows and ender pearls thrown by dummies. They are simulated here (never real entities): arrows are a
 * crit-particle trail that never hurts you, pearls are a portal-particle arc that teleports the dummy.
 */
public final class DummyProjectiles {
	private static final double ARROW_SPEED = 3.0;
	private static final double PEARL_SPEED = 1.5;
	private static final List<Arrow> ARROWS = new ArrayList<>();
	private static final Random RNG = new Random();

	private static final class Arrow {
		Vec3d pos, vel;
		int age;
		PracticeDummy pearlOwner; // non-null = ender pearl
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
		Arrow a = launch(from, to, ARROW_SPEED, 0.05, 0.05);
		if (a == null) return;
		ARROWS.add(a);
		ClientWorld w = MinecraftClient.getInstance().world;
		if (w != null) DummyCombat.playAt(w, from.x, from.y, from.z, SoundEvents.ENTITY_ARROW_SHOOT, 1.0F, 1.0F);
	}

	/** Throw an ender pearl at a spot a few blocks from you; the dummy teleports where it lands. */
	public static void throwPearl(PracticeDummy thrower, ClientPlayerEntity target) {
		double ang = RNG.nextDouble() * Math.PI * 2.0;
		double r = 3.0 + RNG.nextDouble() * 3.0;
		Vec3d to = new Vec3d(target.getX() + Math.cos(ang) * r, target.getY() + 0.5, target.getZ() + Math.sin(ang) * r);
		Vec3d from = thrower.getEyePos();
		Arrow a = launch(from, to, PEARL_SPEED, 0.03, 0.0);
		if (a == null) return;
		a.pearlOwner = thrower;
		ARROWS.add(a);
		ClientWorld w = MinecraftClient.getInstance().world;
		if (w != null) DummyCombat.playAt(w, from.x, from.y, from.z, SoundEvents.ENTITY_ENDER_PEARL_THROW, 1.0F, 1.0F);
	}

	private static Arrow launch(Vec3d from, Vec3d to, double speed, double gravity, double spread) {
		double dx = to.x - from.x, dz = to.z - from.z, dy = to.y - from.y;
		double h = Math.sqrt(dx * dx + dz * dz);
		if (h < 0.01) return null;
		double pitch = solvePitch(h, dy, speed, gravity) + (RNG.nextDouble() - 0.5) * spread;
		double yaw = Math.atan2(dz, dx) + (RNG.nextDouble() - 0.5) * spread;
		Arrow a = new Arrow();
		a.pos = from;
		a.vel = new Vec3d(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch)).multiply(speed);
		return a;
	}

	/** Lowest launch angle at which a drag+gravity projectile reaches (h, dy). */
	private static double solvePitch(double h, double dy, double speed, double gravity) {
		for (double deg = -10.0; deg <= 70.0; deg += 0.5) {
			double ang = Math.toRadians(deg);
			double x = 0.0, y = 0.0, vx = Math.cos(ang) * speed, vy = Math.sin(ang) * speed;
			for (int t = 0; t < 160; t++) {
				x += vx;
				y += vy;
				vx *= 0.99;
				vy = vy * 0.99 - gravity;
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
			boolean pearl = a.pearlOwner != null;
			boolean done = false;
			for (int s = 0; s < 3 && !done; s++) {
				Vec3d next = a.pos.add(a.vel.multiply(1.0 / 3.0));
				BlockPos bp = BlockPos.ofFloored(next);
				if (!world.getBlockState(bp).getCollisionShape(world, bp).isEmpty()) {
					done = true;
					break;
				}
				if (!pearl) {
					Box box = player.getBoundingBox().expand(0.3);
					if (box.contains(next) || box.raycast(a.pos, next).isPresent()) {
						hitPlayer(mc, world, player, a);
						done = true;
						break;
					}
				}
				a.pos = next;
				world.addParticleClient(pearl ? ParticleTypes.PORTAL : ParticleTypes.CRIT, false, false,
					a.pos.x, a.pos.y, a.pos.z, 0.0, 0.0, 0.0);
			}
			a.vel = a.vel.multiply(0.99).add(0.0, pearl ? -0.03 : -0.05, 0.0);
			boolean expired = ++a.age > (pearl ? 120 : 200);
			if (pearl && (done || expired)) land(world, a);
			if (done || expired) it.remove();
		}
	}

	private static void land(ClientWorld world, Arrow a) {
		PracticeDummy d = a.pearlOwner;
		if (d == null || d.isDead() || d.isRemoved()) return;
		for (int i = 0; i < 24; i++) {
			world.addParticleClient(ParticleTypes.PORTAL, false, false, d.getX(), d.getY() + 1.0, d.getZ(),
				(RNG.nextDouble() - 0.5) * 1.5, (RNG.nextDouble() - 0.5), (RNG.nextDouble() - 0.5) * 1.5);
		}
		d.setPosition(a.pos.x, a.pos.y + 0.05, a.pos.z);
		d.setVelocity(Vec3d.ZERO);
		DummyCombat.playAt(world, a.pos.x, a.pos.y, a.pos.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
		for (int i = 0; i < 24; i++) {
			world.addParticleClient(ParticleTypes.PORTAL, false, false, a.pos.x, a.pos.y + 1.0, a.pos.z,
				(RNG.nextDouble() - 0.5) * 1.5, (RNG.nextDouble() - 0.5), (RNG.nextDouble() - 0.5) * 1.5);
		}
	}

	private static void hitPlayer(MinecraftClient mc, ClientWorld world, ClientPlayerEntity player, Arrow a) {
		boolean blocked = player.isBlocking() && player.getRotationVector().dotProduct(a.vel.negate()) > 0.0;
		if (blocked) {
			DummyCombat.playAt(world, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_SHIELD_BLOCK, 1.0F, 1.0F);
			DummyManager.overlay(mc, "\u00a7bYou blocked the arrow");
		} else {
			DummyCombat.playAt(world, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ARROW_HIT_PLAYER, 1.0F, 1.0F);
			DummyHud.flash(0xFF0000);
			DummyManager.overlay(mc, "\u00a7cArrow hit you \u00a77(simulated, no damage, ~"
				+ (int) Math.ceil(a.vel.length() * 2.0) + " would have landed)");
		}
	}
}
