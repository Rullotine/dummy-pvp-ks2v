package com.shubh.practicedummy.client.dummy;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Spawns, tracks and removes the client-side dummies. */
public final class DummyManager {
	private static final int MAX_DUMMIES = 10;
	private static final List<PracticeDummy> DUMMIES = new ArrayList<>();

	// Negative ids can never collide with ids the server hands out.
	private static int nextEntityId = -1_000_000;
	private static ClientWorld trackedWorld;
	/** Set by /dummy gui; the client tick opens the screen after the chat screen has closed. */
	public static boolean openGuiRequested;

	private DummyManager() {}

	public static List<PracticeDummy> all() {
		return DUMMIES;
	}

	public static void clear() {
		DUMMIES.clear();
		trackedWorld = null;
		openGuiRequested = false;
		DummyHud.clear();
		DummyBoost.reset();
	}

	/** Forget dummies when the world is swapped out (dimension change, reconnect...). */
	public static void tick(MinecraftClient client) {
		if (client.world == null || client.world != trackedWorld) {
			DUMMIES.clear();
			DummyHud.clear();
			trackedWorld = client.world;
			return;
		}
		DUMMIES.removeIf(Entity::isRemoved);
	}

	public static PracticeDummy spawnAtCrosshair(MinecraftClient client, DummyMode modeOrNull) {
		ClientPlayerEntity player = client.player;
		if (client.world == null || player == null) return null;

		Vec3d pos;
		HitResult hit = player.raycast(64.0, 1.0F, false);
		if (hit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK) {
			// the air block next to the face you're looking at, so it works on floors, walls and ceilings
			BlockPos free = block.getBlockPos().offset(block.getSide());
			pos = Vec3d.ofBottomCenter(free);
		} else {
			Vec3d look = player.getRotationVector();
			Vec3d flat = new Vec3d(look.x, 0.0, look.z);
			flat = flat.lengthSquared() < 1.0E-4 ? new Vec3d(0.0, 0.0, 1.0) : flat.normalize();
			pos = new Vec3d(player.getX() + flat.x * 3.0, player.getY(), player.getZ() + flat.z * 3.0);
		}
		return spawn(client, pos, modeOrNull != null ? modeOrNull : DummySettings.mode);
	}

	public static PracticeDummy spawn(MinecraftClient client, Vec3d pos, DummyMode mode) {
		ClientWorld world = client.world;
		ClientPlayerEntity player = client.player;
		if (world == null || player == null) return null;

		if (world != trackedWorld) {
			DUMMIES.clear();
			trackedWorld = world;
		}
		if (DUMMIES.size() >= MAX_DUMMIES) {
			overlay(client, "\u00a7cDummy limit reached (" + MAX_DUMMIES + ")");
			return null;
		}

		PracticeDummy dummy = new PracticeDummy(world, new GameProfile(UUID.randomUUID(), "PvPDummy"), mode);
		dummy.setId(nextEntityId--);
		float yaw = (float) (Math.toDegrees(Math.atan2(player.getZ() - pos.z, player.getX() - pos.x)) - 90.0);
		dummy.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0.0F);
		dummy.setHome(pos);
		dummy.setDisplayName(DummySettings.name);
		dummy.setSkinOverride(DummySettings.skin);
		dummy.setMaxHealthValue(DummySettings.maxHealth);
		applyLoadout(dummy);

		world.addEntity(dummy);
		DUMMIES.add(dummy);
		overlay(client, "\u00a7aDummy spawned \u00a77(" + mode.description + ")");
		return dummy;
	}

	public static int removeAll(MinecraftClient client) {
		int count = DUMMIES.size();
		if (client.world != null) {
			for (PracticeDummy dummy : DUMMIES) {
				client.world.removeEntity(dummy.getId(), Entity.RemovalReason.DISCARDED);
			}
		}
		DUMMIES.clear();
		DummyHud.clear();
		return count;
	}

	/** The dummy the stats panel talks about: the one you hit most recently, else the nearest. */
	public static PracticeDummy focus(MinecraftClient client) {
		PracticeDummy best = null;
		int bestScore = Integer.MAX_VALUE;
		ClientPlayerEntity player = client.player;
		for (PracticeDummy d : DUMMIES) {
			int score = d.getTicksSinceHit() < 200 ? d.getTicksSinceHit()
				: 1000 + (player == null ? 0 : (int) player.squaredDistanceTo(d.getX(), d.getY(), d.getZ()));
			if (score < bestScore) {
				bestScore = score;
				best = d;
			}
		}
		return best;
	}

	// ---------- settings ----------
	private static void applyLoadout(PracticeDummy dummy) {
		DummySettings.armor.applyTo(dummy);
		dummy.equipStack(EquipmentSlot.MAINHAND, DummySettings.hold.stack());
		// stun practice: shield in the off-hand (raised by the entity itself); otherwise the usual totem
		dummy.equipStack(EquipmentSlot.OFFHAND, dummy.getMode() == DummyMode.STUNPRACTICE
			? HoldItem.SHIELD.stack() : new ItemStack(Items.TOTEM_OF_UNDYING));
	}

	public static void setMode(MinecraftClient client, DummyMode mode) {
		DummySettings.mode = mode;
		for (PracticeDummy dummy : DUMMIES) {
			dummy.setMode(mode);
			applyLoadout(dummy);
		}
		overlay(client, "\u00a7aDummy: " + mode.description);
	}

	public static void cycleMode(MinecraftClient client) {
		setMode(client, DummySettings.mode.next());
	}

	public static void setArmor(MinecraftClient client, ArmorSet set) {
		DummySettings.armor = set;
		for (PracticeDummy dummy : DUMMIES) applyLoadout(dummy);
		overlay(client, "\u00a7aDummy armor: " + set.name().toLowerCase());
	}

	public static void setHold(MinecraftClient client, HoldItem hold) {
		DummySettings.hold = hold;
		for (PracticeDummy dummy : DUMMIES) applyLoadout(dummy);
		overlay(client, "\u00a7aDummy holds: " + hold.name().toLowerCase());
	}

	public static void setName(MinecraftClient client, String name) {
		DummySettings.name = name;
		for (PracticeDummy dummy : DUMMIES) dummy.setDisplayName(name);
		overlay(client, "\u00a7aDummy name: " + name);
	}

	public static void setMaxHealth(MinecraftClient client, float hp) {
		DummySettings.maxHealth = hp;
		for (PracticeDummy dummy : DUMMIES) dummy.setMaxHealthValue(hp);
		overlay(client, "\u00a7aDummy health: " + hp + " HP (" + hp / 2.0F + " hearts)");
	}

	/** Copy the skin of someone in the tab list (or "self" to go back to yours). Returns false if not found. */
	public static boolean setSkin(MinecraftClient client, String playerName) {
		if (playerName.equalsIgnoreCase("self")) {
			DummySettings.skin = null;
			for (PracticeDummy dummy : DUMMIES) dummy.setSkinOverride(null);
			return true;
		}
		ClientPlayNetworkHandler handler = client.getNetworkHandler();
		PlayerListEntry entry = handler == null ? null : handler.getPlayerListEntry(playerName);
		if (entry == null) return false;
		DummySettings.skin = entry.getSkinTextures();
		for (PracticeDummy dummy : DUMMIES) dummy.setSkinOverride(DummySettings.skin);
		return true;
	}

	static void overlay(MinecraftClient client, String message) {
		if (client.inGameHud != null) client.inGameHud.setOverlayMessage(Text.literal(message), false);
	}
}
