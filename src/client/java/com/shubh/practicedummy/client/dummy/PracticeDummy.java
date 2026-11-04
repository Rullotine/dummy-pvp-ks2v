package com.shubh.practicedummy.client.dummy;

import com.mojang.authlib.GameProfile;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * A client-only fake player. It is a real AbstractClientPlayerEntity in the client world, so the
 * game treats it as a player (crosshair targeting, rendering, F3, hitboxes...), but the server never
 * hears about it. Movement is simulated here because no server packets will ever move it.
 */
public class PracticeDummy extends OtherClientPlayerEntity {
	private static final double WALK_SPEED = 0.2;
	private static final double STRAFE_RADIUS = 3.0;
	private static final int IFRAMES = 10;

	private final Random rng = new Random();
	private DummyMode mode;
	private Vec3d home = Vec3d.ZERO;

	private int iFrames;
	private int knockbackTicks;
	private int combo;
	private int ticksSinceHit = 10_000;

	private int wanderTicks;
	private boolean pausing;
	private double wanderAngle;
	private int strafeTicks;
	private int strafeDir = 1;

	public PracticeDummy(ClientWorld world, GameProfile profile, DummyMode mode) {
		super(world, profile);
		this.mode = mode;
	}

	/** Use the local player's skin so the dummy looks like a real opponent (and not a default Steve/Alex). */
	@Override
	public SkinTextures getSkin() {
		ClientPlayerEntity self = MinecraftClient.getInstance().player;
		return self != null ? self.getSkin() : super.getSkin();
	}

	public void setHome(Vec3d home) {
		this.home = home;
	}

	public DummyMode getMode() {
		return mode;
	}

	public void setMode(DummyMode mode) {
		this.mode = mode;
	}

	/** Vanilla-style damage immunity window so rapid clicks don't count multiple times. */
	public boolean canBeHit() {
		return iFrames <= 0;
	}

	/** Called by the combat code when a hit lands. Returns the current combo count. */
	public int registerHit() {
		combo = ticksSinceHit <= 40 ? combo + 1 : 1;
		ticksSinceHit = 0;
		iFrames = IFRAMES;
		knockbackTicks = 10; // let knockback play out instead of the AI overriding it
		return combo;
	}

	@Override
	public void tick() {
		if (iFrames > 0) iFrames--;
		if (ticksSinceHit < 10_000) ticksSinceHit++;
		simulateMovement();
		super.tick(); // after moving, so limb/walk animation sees the movement
	}

	private void simulateMovement() {
		ClientPlayerEntity target = MinecraftClient.getInstance().player;
		double dirX = 0.0, dirZ = 0.0;
		boolean moving = false;
		boolean hasFacing = false;
		float facing = 0.0F;

		double toPlayerX = 0.0, toPlayerZ = 0.0, toPlayerDist = 0.0;
		if (target != null) {
			toPlayerX = target.getX() - getX();
			toPlayerZ = target.getZ() - getZ();
			toPlayerDist = Math.sqrt(toPlayerX * toPlayerX + toPlayerZ * toPlayerZ);
		}

		switch (mode) {
			case WALK -> {
				if (--wanderTicks <= 0) {
					wanderTicks = 30 + rng.nextInt(60);
					pausing = rng.nextInt(4) == 0;
					wanderAngle = rng.nextDouble() * Math.PI * 2.0;
					double hx = home.x - getX(), hz = home.z - getZ();
					if (hx * hx + hz * hz > 36.0) wanderAngle = Math.atan2(hz, hx); // drift back home
				}
				if (!pausing) {
					dirX = Math.cos(wanderAngle);
					dirZ = Math.sin(wanderAngle);
					moving = true;
					facing = (float) (Math.toDegrees(Math.atan2(dirZ, dirX)) - 90.0);
					hasFacing = true;
				}
			}
			case STRAFE -> {
				if (toPlayerDist > 0.01) {
					double nx = toPlayerX / toPlayerDist, nz = toPlayerZ / toPlayerDist;
					double radial = MathHelper.clamp((toPlayerDist - STRAFE_RADIUS) * 0.5, -1.0, 1.0);
					dirX = -nz * strafeDir + nx * radial;
					dirZ = nx * strafeDir + nz * radial;
					double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
					if (len > 1.0) {
						dirX /= len;
						dirZ /= len;
					}
					moving = true;
					facing = (float) (Math.toDegrees(Math.atan2(toPlayerZ, toPlayerX)) - 90.0);
					hasFacing = true;
				}
				if (--strafeTicks <= 0) {
					strafeTicks = 20 + rng.nextInt(40);
					strafeDir = -strafeDir;
				}
			}
			case STAND -> {
				if (toPlayerDist > 0.01) {
					facing = (float) (Math.toDegrees(Math.atan2(toPlayerZ, toPlayerX)) - 90.0);
					hasFacing = true;
				}
			}
		}

		Vec3d v = getVelocity();
		double vx = v.x, vy = v.y, vz = v.z;
		boolean ground = isOnGround();

		if (knockbackTicks > 0) {
			knockbackTicks--;
			double friction = ground ? 0.546 : 0.91;
			vx *= friction;
			vz *= friction;
		} else {
			double accel = ground ? 0.6 : 0.15;
			double tx = moving ? dirX * WALK_SPEED : 0.0;
			double tz = moving ? dirZ * WALK_SPEED : 0.0;
			vx += (tx - vx) * accel;
			vz += (tz - vz) * accel;
		}

		if (isTouchingWater()) {
			vy = Math.min(vy + 0.04, 0.12); // float up instead of sinking
		} else {
			vy = (vy - 0.08) * 0.98;
		}
		if (ground && horizontalCollision && moving) {
			vy = 0.42; // hop over blocks in the way
		}

		setVelocity(vx, vy, vz);
		move(MovementType.SELF, new Vec3d(vx, vy, vz));

		if (getY() < home.y - 64.0) { // fell into the void
			setPosition(home.x, home.y, home.z);
			setVelocity(Vec3d.ZERO);
		}

		if (hasFacing) {
			setYaw(facing);
			this.headYaw = facing;
		}
	}
}
