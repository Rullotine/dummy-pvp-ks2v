package com.shubh.practicedummy.client.dummy;

import com.mojang.authlib.GameProfile;
import java.util.ArrayDeque;
import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * A client-only fake player. Movement, health, totems, shield and stats are simulated here;
 * the server never hears about it.
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
	private double mx, my, mz;
	private int combo;
	private int ticksSinceHit = 10_000;

	// shield / stun practice
	private int shieldDisabledTicks;
	private int shieldDisabledAge = -10_000;
	private int blocks, stuns, stunSlams;
	private int bestReaction = -1;

	// stats
	private int hits, crits, smashes, bestCombo, lifeHits, lastPopHits;
	private float totalDamage, bestHit, bestSmash;
	private int lifeStartAge, lastPopTicks = -1;
	private final ArrayDeque<float[]> log = new ArrayDeque<>();

	// AI state
	private int wanderTicks;
	private boolean pausing;
	private double wanderAngle;
	private int strafeTicks;
	private int strafeDir = 1;
	private int fightPhase;
	private int hopTicks;
	private int swingCooldown;
	private int bowDraw = -1;
	private int bowCooldown = 30;

	// totems / death / gadgets
	private int totemsLeft = DummySettings.totems;
	private int totemReadyAge;
	private boolean dead;
	private int respawnTicks;
	private int kills;
	private boolean drillOpen;
	private int drillCueAge;
	private int drillNext = 60;
	private int pearlTimer = 100;
	private int windTimer = 60;

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

	public float getDummyHealth() { return dummyHealth; }
	public float getDummyMaxHealth() { return dummyMaxHealth; }
	public float getDummyAbsorption() { return dummyAbsorption; }
	public int getTotemPops() { return totemPops; }
	public int getRegenTicks() { return regenTicks; }
	public int getAbsorptionTicks() { return absorptionTicks; }

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
		lastPopTicks = Math.max(1, age - lifeStartAge);
		totemReadyAge = age + DummySettings.refill;
		lastPopHits = lifeHits;
		lifeHits = 0;
		dummyHealth = Math.min(1.0F, dummyMaxHealth);
		dummyAbsorption = 8.0F;
		absorptionTicks = 100;
		regenTicks = 900;
	}

	public float getLastPopSeconds() { return lastPopTicks < 0 ? -1.0F : lastPopTicks / 20.0F; }
	public int getLastPopHits() { return lastPopHits; }

	// ---------- hit handling ----------
	public boolean canBeHit() {
		return iFrames <= 0 && !dead;
	}

	public void flashHurt() {
		this.maxHurtTime = 10;
		this.hurtTime = 10;
	}

	/** Vanilla knockback formula: (x, z) points from the dummy toward the attacker. */
	public void applyKnockback(double strength, double x, double z) {
		if (DummySettings.armorCalc) strength *= 1.0 - DummySettings.armor.knockbackResist;
		if (strength <= 0.0) return;
		while (x * x + z * z < 1.0E-5) {
			x = (rng.nextDouble() - rng.nextDouble()) * 0.01;
			z = (rng.nextDouble() - rng.nextDouble()) * 0.01;
		}
		double len = Math.sqrt(x * x + z * z);
		mx = mx / 2.0 - x / len * strength;
		mz = mz / 2.0 - z / len * strength;
		if (isOnGround()) my = Math.min(0.4, my / 2.0 + strength);
		knockedBack = true;
		knockbackAge = 0;
	}

	public int registerHit() {
		combo = ticksSinceHit <= 40 ? combo + 1 : 1;
		ticksSinceHit = 0;
		iFrames = IFRAMES;
		knockedBack = true;
		knockbackAge = 0;
		bestCombo = Math.max(bestCombo, combo);
		return combo;
	}

	// ---------- shield ----------
	/** Shield is up whenever the dummy holds one and it isn't disabled. */
	public boolean isShieldUp() {
		return !dead && shieldDisabledTicks <= 0 && getMainHandStack().isOf(Items.SHIELD);
	}

	public boolean hasShield() {
		return getMainHandStack().isOf(Items.SHIELD);
	}

	public boolean isShieldDisabled() {
		return shieldDisabledTicks > 0;
	}

	public int getShieldDisabledTicks() { return shieldDisabledTicks; }
	public int getTicksSinceShieldDisabled() { return age - shieldDisabledAge; }

	public void disableShield(int ticks) {
		shieldDisabledTicks = ticks;
		shieldDisabledAge = age;
		stuns++;
	}

	public void noteBlocked() { blocks++; }

	public void recordStunSlam(int reactionTicks) {
		stunSlams++;
		if (bestReaction < 0 || reactionTicks < bestReaction) bestReaction = reactionTicks;
	}

	public int getBlocks() { return blocks; }
	public int getStuns() { return stuns; }
	public int getStunSlams() { return stunSlams; }
	public int getBestReaction() { return bestReaction; }

	/** True if (ax, az) is in front of the dummy (the 180 degrees a shield covers). */
	public boolean isFacing(double ax, double az) {
		double dx = ax - getX(), dz = az - getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0E-4) return true;
		double yaw = Math.toRadians(getYaw());
		return (dx * -Math.sin(yaw) + dz * Math.cos(yaw)) / len > 0.0;
	}

	// ---------- stats ----------
	public void recordHit(float dmg, boolean crit, boolean smash) {
		hits++;
		totalDamage += dmg;
		bestHit = Math.max(bestHit, dmg);
		if (crit) crits++;
		if (smash) {
			smashes++;
			bestSmash = Math.max(bestSmash, dmg);
		}
		if (lifeHits++ == 0) lifeStartAge = age;
		log.addLast(new float[] {age, dmg});
	}

	public float getDps() {
		while (!log.isEmpty() && age - log.peekFirst()[0] > 100) log.removeFirst();
		if (log.isEmpty()) return 0.0F;
		float sum = 0.0F;
		for (float[] e : log) sum += e[1];
		float window = MathHelper.clamp((age - log.peekFirst()[0]) / 20.0F, 1.0F, 5.0F);
		return sum / window;
	}

	public int getHits() { return hits; }
	public int getCrits() { return crits; }
	public int getSmashes() { return smashes; }
	public float getTotalDamage() { return totalDamage; }
	public float getBestHit() { return bestHit; }
	public float getBestSmash() { return bestSmash; }
	public int getBestCombo() { return bestCombo; }
	public int getCombo() { return ticksSinceHit <= 40 ? combo : 0; }

	public void resetStats() {
		hits = crits = smashes = bestCombo = lifeHits = lastPopHits = 0;
		totalDamage = bestHit = bestSmash = 0.0F;
		totemPops = 0;
		lastPopTicks = -1;
		blocks = stuns = stunSlams = 0;
		bestReaction = -1;
		log.clear();
	}

	@Override
	public void tick() {
		if (dead) {
			if (DummySettings.respawn && --respawnTicks <= 0) revive();
			super.tick();
			return;
		}
		if (iFrames > 0) iFrames--;
		if (ticksSinceHit < 10_000) ticksSinceHit++;
		if (shieldDisabledTicks > 0) shieldDisabledTicks--;
		if (absorptionTicks > 0 && --absorptionTicks == 0) dummyAbsorption = 0.0F;
		if (regenTicks > 0) {
			regenTicks--;
			if (regenTicks % 25 == 0 && dummyHealth < dummyMaxHealth) {
				dummyHealth = Math.min(dummyMaxHealth, dummyHealth + 1.0F);
			}
		} else if (DummySettings.autoHeal && ticksSinceHit > 100 && dummyHealth < dummyMaxHealth && age % 4 == 0) {
			dummyHealth = Math.min(dummyMaxHealth, dummyHealth + 1.0F);
			if (dummyHealth >= dummyMaxHealth) lifeHits = 0;
		}
		runGadgets();
		simulateMovement();
		syncUsePose(isShieldUp() || bowDraw >= 0);
		super.tick();
	}

	/** Raise the shield / draw the bow visually. Remote players need the "using item" flag set by hand. */
	private void syncUsePose(boolean want) {
		if (want && !isUsingItem()) {
			setCurrentHand(Hand.MAIN_HAND);
			if (!isUsingItem()) setLivingFlag(1, true);
		} else if (!want && isUsingItem()) {
			clearActiveItem();
			if (isUsingItem()) setLivingFlag(1, false);
		}
	}

	/** Vanilla attack speed -> ticks between swings. */
	private int attackInterval() {
		ItemStack s = getMainHandStack();
		if (s.isEmpty()) return 5;
		String path = net.minecraft.registry.Registries.ITEM.getId(s.getItem()).getPath();
		if (s.isOf(Items.MACE)) return 33;        // 0.6 attacks/s
		if (path.endsWith("_axe")) return 20;     // 1.0 attacks/s
		if (path.endsWith("_sword")) return 13;   // 1.6 attacks/s
		if (path.endsWith("_spear")) return 17;   // approximate
		return 5;
	}

	// ---------- totems, death, drill, gadgets ----------
	/** True if a totem saves the dummy from a lethal hit (none left, or still re-equipping = it dies). */
	public boolean tryTotem() {
		if (age < totemReadyAge) return false;
		if (DummySettings.totems >= 0) {
			if (totemsLeft <= 0) return false;
			totemsLeft--;
		}
		return true;
	}

	public int getTotemsLeft() { return DummySettings.totems < 0 ? -1 : totemsLeft; }
	public void resetTotems() { totemsLeft = DummySettings.totems; }
	public boolean isDead() { return dead; }
	public int getKills() { return kills; }
	public int getLifeHits() { return lifeHits; }
	public float getLifeSeconds() { return Math.max(1, age - lifeStartAge) / 20.0F; }

	public void die() {
		dead = true;
		kills++;
		respawnTicks = 70;
		dummyHealth = 0.0F;
		dummyAbsorption = 0.0F;
		shieldDisabledTicks = 0;
		bowDraw = -1;
		drillOpen = false;
		mx = 0.0;
		mz = 0.0;
		lifeHits = 0;
		setSprinting(false);
		syncUsePose(false);
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
			EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
			equipStack(slot, ItemStack.EMPTY);
		}
		setInvisible(true);
	}

	private void revive() {
		dead = false;
		setInvisible(false);
		dummyHealth = dummyMaxHealth;
		dummyAbsorption = 0.0F;
		absorptionTicks = 0;
		regenTicks = 0;
		totemsLeft = DummySettings.totems;
		totemReadyAge = 0;
		iFrames = 0;
		knockedBack = false;
		mx = 0.0;
		my = 0.0;
		mz = 0.0;
		setPosition(home.x, home.y, home.z);
		setVelocity(Vec3d.ZERO);
		DummyManager.reapply(MinecraftClient.getInstance());
		DummyCombat.respawnFx(this);
		DummyCombat.taunt(this, new String[] {"I'm back", "round 2", "again?"});
	}

	public boolean isDrillOpen() { return !dead && drillOpen && shieldDisabledTicks > 0; }

	/** Returns ticks since the drill cue if the shield was open (and closes the window), else -1. */
	public int consumeDrillOpen() {
		if (!drillOpen) return -1;
		drillOpen = false;
		return age - drillCueAge;
	}

	private void runGadgets() {
		ClientPlayerEntity target = MinecraftClient.getInstance().player;
		if (DummySettings.drill && hasShield()) {
			if (drillOpen && shieldDisabledTicks <= 0) {
				drillOpen = false;
				DummyTraining.onMissedCue();
			}
			if (--drillNext <= 0) {
				drillNext = 50 + rng.nextInt(70);
				shieldDisabledTicks = 14;
				shieldDisabledAge = age;
				drillOpen = true;
				drillCueAge = age;
				DummySounds.cue();
			}
		}
		if (target != null && DummySettings.pearl && (mode == DummyMode.FIGHT || mode == DummyMode.RUNNER
			|| mode == DummyMode.STRAFE) && --pearlTimer <= 0) {
			pearlTimer = 100 + rng.nextInt(140);
			DummyProjectiles.throwPearl(this, target);
		}
		if (DummySettings.windCharge && isOnGround() && --windTimer <= 0) {
			windTimer = 70 + rng.nextInt(80);
			my = 1.0;
			if (target != null) {
				double dx = target.getX() - getX(), dz = target.getZ() - getZ();
				double len = Math.sqrt(dx * dx + dz * dz);
				if (len > 0.01) {
					mx = dx / len * 0.3;
					mz = dz / len * 0.3;
				}
			}
			knockedBack = true;
			knockbackAge = 0;
			DummyCombat.windFx(this);
		}
	}

	// ---------- movement / AI ----------
	private void simulateMovement() {
		ClientPlayerEntity target = MinecraftClient.getInstance().player;
		double dirX = 0.0, dirZ = 0.0;
		boolean moving = false, sprint = false, hop = false, hasFacing = false;
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
		boolean inWater = isTouchingWater();
		boolean archer = getMainHandStack().isOf(Items.BOW) || getMainHandStack().isOf(Items.CROSSBOW);
		int drawTicks = getMainHandStack().isOf(Items.CROSSBOW) ? 25 : 20;

		switch (mode) {
			case WALK -> {
				if (--wanderTicks <= 0) {
					wanderTicks = 30 + rng.nextInt(60);
					pausing = rng.nextInt(4) == 0;
					wanderAngle = rng.nextDouble() * Math.PI * 2.0;
					double hx = home.x - getX(), hz = home.z - getZ();
					if (hx * hx + hz * hz > 36.0) wanderAngle = Math.atan2(hz, hx);
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
					if (archer) {
						side = 0.8;
						radial = (dist - 12.0) * 0.5; // kite at range
					} else {
						switch (fightPhase) {
							case 0 -> { side = 1.0; radial = (dist - 2.6) * 0.8; }
							case 1 -> { side = 0.5; radial = (dist - 2.0) * 1.0; }
							case 2 -> { side = 0.3; radial = (dist - 4.2) * 1.0; }
							default -> { side = 0.8; radial = (dist - 3.0) * 0.8; }
						}
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

					if (archer) {
						if (bowDraw < 0) {
							if (--bowCooldown <= 0 && dist < 40.0 && target != null) bowDraw = 0;
						} else if (++bowDraw >= drawTicks && target != null) {
							DummyProjectiles.shoot(this, target);
							swingHand(Hand.MAIN_HAND);
							bowDraw = -1;
							bowCooldown = 15 + rng.nextInt(25);
						}
					} else {
						bowDraw = -1;
						if (--hopTicks <= 0) {
							hopTicks = 10 + rng.nextInt(20);
							hop = !isShieldUp();
						}
						// vanilla reach is 3 blocks, and swings follow the weapon's attack speed
						if (swingCooldown > 0) swingCooldown--;
						if (dist < 3.0 && swingCooldown <= 0 && !isShieldUp()) {
							swingCooldown = attackInterval() + rng.nextInt(3);
							if (DummySettings.hitback) DummyCombat.dummyAttack(this, target);
							swingHand(Hand.MAIN_HAND);
						}
					}
				}
			}
			case RUNNER -> {
				if (--strafeTicks <= 0) {
					strafeTicks = 10 + rng.nextInt(20);
					strafeDir = -strafeDir;
				}
				if (dist > 0.01) {
					double flee = dist < 9.0 ? 1.0 : 0.0;
					double side = horizontalCollision ? 1.0 : 0.25;
					dirX = -nx * flee - nz * side * strafeDir;
					dirZ = -nz * flee + nx * side * strafeDir;
					double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
					if (len > 1.0) {
						dirX /= len;
						dirZ /= len;
					}
					moving = len > 0.05;
					sprint = moving;
					facing = (float) (Math.toDegrees(Math.atan2(dirZ, dirX)) - 90.0);
					hasFacing = moving;
				}
			}
			case JUMPER -> {
				if (dist > 0.01) {
					facing = faceTarget;
					hasFacing = true;
				}
				if (--hopTicks <= 0) {
					hopTicks = 8 + rng.nextInt(8);
					hop = true;
				}
			}
			case STAND, STUNPRACTICE -> {
				if (dist > 0.01) {
					facing = faceTarget;
					hasFacing = true;
				}
			}
		}

		if (bowDraw >= 0 && target != null) {
			facing = faceTarget;
			hasFacing = true;
		}
		boolean swimming = inWater && DummySettings.swim;
		if (swimming && moving) sprint = true; // sprint + water = vanilla swimming pose
		if (isSprinting() != sprint) setSprinting(sprint);

		double vx = mx, vy = my, vz = mz;
		boolean ground = isOnGround();

		if (knockedBack) {
			knockbackAge++;
			double drag = inWater ? 0.8 : ground ? 0.546 : 0.91;
			vx *= drag;
			vz *= drag;
			if (knockbackAge > 3 && (ground || inWater) && Math.hypot(vx, vz) < 0.06) knockedBack = false;
			if (knockbackAge > 60) knockedBack = false;
		} else {
			double speed = sprint ? WALK_SPEED * 1.3 : WALK_SPEED;
			if (inWater) speed *= sprint ? 0.8 : 0.5;
			double accel = inWater ? 0.2 : ground ? 0.6 : 0.15;
			double tx = moving ? dirX * speed : 0.0;
			double tz = moving ? dirZ * speed : 0.0;
			vx += (tx - vx) * accel;
			vz += (tz - vz) * accel;
		}

		if (inWater) {
			if (swimming) {
				double want = 0.02; // neutral buoyancy
				boolean chase = target != null && (mode == DummyMode.FIGHT || mode == DummyMode.STRAFE);
				if (chase) want += MathHelper.clamp((target.getY() - getY()) * 0.05, -0.06, 0.06);
				else if (isSubmergedInWater()) want = 0.06; // surface and bob
				vy = vy * 0.8 - 0.02 + want;
				if (horizontalCollision && moving) vy = Math.max(vy, 0.3); // climb out onto ledges
			} else {
				vy = Math.min(vy + 0.04, 0.12);
			}
		} else {
			vy = (vy - 0.08) * 0.98;
		}
		if (ground && !knockedBack && !inWater && ((horizontalCollision && moving) || hop)) {
			vy = 0.42;
		}

		setVelocity(vx, vy, vz);
		move(MovementType.SELF, new Vec3d(vx, vy, vz));
		Vec3d after = getVelocity();
		mx = after.x;
		my = after.y;
		mz = after.z;

		if (getY() < home.y - 64.0) {
			setPosition(home.x, home.y, home.z);
			setVelocity(Vec3d.ZERO);
			mx = 0.0;
			my = 0.0;
			mz = 0.0;
		}

		if (hasFacing) {
			setYaw(facing);
			this.headYaw = facing;
		}
	}
}
