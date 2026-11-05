package com.shubh.practicedummy.client.dummy;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Per-tick checks for things the server would normally resolve: your arrows/tridents and spear charges. */
public final class DummyTracker {
	private DummyTracker() {}

	public static void tick(MinecraftClient mc) {
		ClientWorld world = mc.world;
		ClientPlayerEntity player = mc.player;
		if (world == null || player == null || DummyManager.all().isEmpty()) return;

		List<PersistentProjectileEntity> projectiles = new ArrayList<>();
		for (Entity entity : world.getEntities()) {
			if (entity instanceof PersistentProjectileEntity p) projectiles.add(p);
		}
		for (PersistentProjectileEntity p : projectiles) {
			if (p.isRemoved() || p.isInGround() || p.isNoClip()) continue;
			if (p.getOwner() != player) continue;
			Vec3d now = new Vec3d(p.getX(), p.getY(), p.getZ());
			Vec3d before = new Vec3d(p.lastX, p.lastY, p.lastZ);
			for (PracticeDummy dummy : new ArrayList<>(DummyManager.all())) {
				Box box = dummy.getBoundingBox().expand(0.3);
				if (box.contains(now) || box.raycast(before, now).isPresent()) {
					DummyCombat.projectile(mc, player, dummy, p);
					world.removeEntity(p.getId(), Entity.RemovalReason.DISCARDED);
					break;
				}
			}
		}

		DummyCombat.chargeCheck(mc);
	}
}
