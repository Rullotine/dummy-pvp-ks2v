package com.shubh.practicedummy.client.dummy;

import com.mojang.authlib.GameProfile;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * A client-only fake player. It is a real AbstractClientPlayerEntity in the client world, so the
 * game treats it as a player (crosshair targeting, rendering, F3, hitboxes...), but the server never
 * hears about it. Movement, health and totems are simulated here.
 */
public class PracticeDummy extends OtherClientPlayerEntity {
	private static final double WALK_SPEED = 0.2;
	private static final int IFRAMES = 10;

	private final Random rng = new Random();
	private DummyMode mode;
	private Vec3d home = Vec3d.ZERO;
	private Text displayName = Text.literal("Dummy");
	private SkinTextures skinOverride;

	// combat state
	private float dummyMaxHealth = 20.0F;
	private float dummyHealth = 20.0F;
	private float dummyAbsorption;
	private int absorptionTicks;
	private int regenTicks;
	private int totemPops;
	private int iFrames;
	private boolean knockedBack;
	private int knockbackAge;
	private int combo;
	private int ticksSinceHit = 10_000;

	// AI state
	private int wanderTicks;
	private boolean pausing;
	private double wanderAngle;
	private int strafeTicks;
	private int strafeDir = 1;
	private int fightPhase;
	private int hopTicks;
	private int swingCooldown;

	public PracticeDummy(ClientWorld world, GameProfile profile, DummyMode mode) {
		super(world, profile);
		this.mode = mode;
	}

	// ---------- identity ----------
	@Override
	public Text getName() {
		return displayName;
	}

	public void setDisplayName(String name) {
		this.displayName = Text.literal(name);
	}

	/** Own skin by default, or whatever skin was picked with /dummy skin. */
	@Override
	public SkinTextures getSkin() {
		if (skinOverride != null) return skinOverride;
		ClientPlayerEntity self = MinecraftClient.getInstance().player;
		return self != null ? self.getSkin() : super.getSkin();
	}

	public void setSkinOverride(SkinTextures skin) {
		this.skinOverride = skin;
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

	// ---------- health / totems ----------
	public void setMaxHealthValue(float hp) {
		this.dummyMaxHealth = Math.max(1.0F, hp);
		this.dummyHealth = this.dummyMaxHealth;
		this.dummyAbsorption = 0.0F;
	}

	public float getDummyHealth() {
		return dummyHealth;
	}

	public float getDummyMaxHealth() {
		return dummyMaxHealth;
	}

	public float getDummyAbsorption() {
		return dummyAbsorption;
	}

	public int getTotemPops() {
		return totemPops;
	}

	/** Absorption first, then health. Returns true if this hit would have killed (=> pop a totem). */
	public boolean applyDamage(float damage) {
		float remaining = damage;
		if (dummyAbsorption > 0.0F) {
			float soaked = Math.min(dummyAbsorption, remaining);
			dummyAbsorption -= soaked;
			remaining -= soaked;
		}
		dummyHealth -= remaining;
		return dummyHealth <= 0.0F;
	}

	/** Same effects as a real totem: 1 heart, Absorption II for 5s, Regeneration II for 45s. */
	public void popTotem() {
		totemPops++;
		dummyHealth = Math.min(1.0F, dummyMaxHealth);
		dummyAbsorption = 8.0F;
		absorptionTicks = 100;
		regenTicks = 900;
	}

	// ---------- hit handling ----------
	public boolean canBeHit() {
		return iFrames <= 0;
	}

	/** Red hurt flash on the model (the game tints any living entity with hurtTime > 0). */
	public void flashHurt() {
		this.maxHurtTime = 10;
		this.hurtTime = 10;
	}

	/** Called when a hit lands. Returns the current combo count. */
	public int registerHit() {
		combo = ticksSinceHit <= 40 ? combo + 1 : 1;
		ticksSinceHit = 0;
		iFrames = IFRAMES;
		knockedBack = true;
		knockbackAge = 0;
		return combo;
	}

	@Override
	public void tick() {
		if (iFrames > 0) iFrames--;
		if (ticksSinceHit < 10_000) ticksSinceHit++;
		if (absorptionTicks > 0 && --absorptionTicks == 0) dummyAbsorption = 0.0F;
		if (regenTicks > 0) {
			regenTicks--;
			if (regenTicks % 25 == 0 && dummyHealth < dummyMaxHealth) {
				dummyHealth = Math.min(dummyMaxHealth, dummyHealth + 1.0F);
			}
		}
		simulateMovement();
		super.tick(); // after moving, so limb/walk animation sees the movement
	}

	// ---------- movement / AI ----------
	private void simulateMovement() {
		ClientPlayerEntity target = MinecraftClient.getInstance().player;
		double dirX = 0.0, dirZ = 0.0;
		boolean moving = false;
		boolean sprint = false;
		boolean hop = false;
		boolean hasFacing = false;
		float facing = 0.0F;

		double toPlayerX = 0.0, toPlayerZ = 0.0, dist = 0.0;
		if (target != null) {
			toPlayerX = target.getX() - getX();
			toPlayerZ = target.getZ() - getZ();
			dist = Math.sqrt(toPlayerX * toPlayerX + toPlayerZ * toPlayerZ);
		}
		double nx = dist > 0.01 ? toPlayerX / dist : 0.0;
		double nz = dist > 0.01 ? toPlayerZ / dist : 0.0;
		float faceTarget = (float) (Math.toDegrees(Math.atan2(toPlayerZ, toPlayerX)) - 90.0);

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
				if (dist > 0.01) {
					double radial = MathHelper.clamp((dist - 3.0) * 0.5, -1.0, 1.0);
					dirX = -nz * strafeDir + nx * radial;
					dirZ = nx * strafeDir + nz * radial;
					moving = true;
					facing = faceTarget;
					hasFacing = true;
				}
				if (--strafeTicks <= 0) {
					strafeTicks = 20 + rng.nextInt(40);
					strafeDir = -strafeDir;
				}
			}
			case FIGHT -> {
				if (--strafeTicks <= 0) {
					strafeTicks = 12 + rng.nextInt(24);
					fightPhase = rng.nextInt(4);
					strafeDir = rng.nextBoolean() ? 1 : -1;
				}
				if (dist > 0.01) {
					double side, radial;
					switch (fightPhase) {
						case 0 -> { side = 1.0; radial = (dist - 2.6) * 0.8; }          // circle, hold range
						case 1 -> { side = 0.5; radial = (dist - 2.0) * 1.0; }          // push in
						case 2 -> { side = 0.3; radial = (dist - 4.2) * 1.0; }          // back off
						default -> { side = 0.8; radial = (dist - 3.0) * 0.8; }
					}
					radial = MathHelper.clamp(radial, -1.0, 1.0);
					dirX = -nz * side * strafeDir + nx * radial;
					dirZ = nx * side * strafeDir + nz * radial;
					double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
					if (len > 1.0) {
						dirX /= len;
						dirZ /= len;
					}
					moving = true;
					sprint = radial > 0.4;
					facing = faceTarget;
					hasFacing = true;

					if (--hopTicks <= 0) {
						hopTicks = 10 + rng.nextInt(20);
						hop = true;
					}
					if (dist < 3.6 && --swingCooldown <= 0) {
						swingCooldown = 8 + rng.nextInt(8);
						swingHand(Hand.MAIN_HAND);
					}
				}
			}
			case STAND -> {
				if (dist > 0.01) {
					facing = faceTarget;
					hasFacing = true;
				}
			}
		}

		if (isSprinting() != sprint) setSprinting(sprint);

		Vec3d v = getVelocity();
		double vx = v.x, vy = v.y, vz = v.z;
		boolean ground = isOnGround();

		if (knockedBack) {
			// let knockback play out exactly like vanilla: ground/air drag only, no AI steering
			knockbackAge++;
			double drag = ground ? 0.546 : 0.91;
			vx *= drag;
			vz *= drag;
			if (knockbackAge > 3 && ground && Math.hypot(vx, vz) < 0.06) knockedBack = false;
			if (knockbackAge > 60) knockedBack = false;
		} else {
			double speed = sprint ? WALK_SPEED * 1.3 : WALK_SPEED;
			double accel = ground ? 0.6 : 0.15;
			double tx = moving ? dirX * speed : 0.0;
			double tz = moving ? dirZ * speed : 0.0;
			vx += (tx - vx) * accel;
			vz += (tz - vz) * accel;
		}

		if (isTouchingWater()) {
			vy = Math.min(vy + 0.04, 0.12); // float up instead of sinking
		} else {
			vy = (vy - 0.08) * 0.98;
		}
		if (ground && !knockedBack && ((horizontalCollision && moving) || hop)) {
			vy = 0.42; // hop over blocks / bunny-hop while fighting
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
