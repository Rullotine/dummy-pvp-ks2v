package com.shubh.practicedummy.client.dummy;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Floating damage numbers. They are drawn on the HUD at the projected screen position of a world
 * point (first-person only), which avoids touching the world renderer at all.
 */
public final class DummyHud {
	private static final int LIFETIME = 26;
	private static final double RISE_PER_TICK = 0.04;
	private static final List<Floater> FLOATERS = new ArrayList<>();

	private static final class Floater {
		final double x, y, z;
		final String text;
		final int rgb;
		int age;

		Floater(double x, double y, double z, String text, int rgb) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.text = text;
			this.rgb = rgb;
		}
	}

	private DummyHud() {}

	public static void add(double x, double y, double z, String text, int rgb) {
		FLOATERS.add(new Floater(x, y, z, text, rgb));
	}

	public static void clear() {
		FLOATERS.clear();
	}

	public static void tick() {
		for (Floater f : FLOATERS) f.age++;
		FLOATERS.removeIf(f -> f.age > LIFETIME);
	}

	public static void render(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player == null || FLOATERS.isEmpty() || mc.options.hudHidden) return;
		if (!mc.options.getPerspective().isFirstPerson()) return;

		float pt = counter.getTickProgress(false);
		Vec3d cam = player.getCameraPosVec(pt);
		double yaw = Math.toRadians(player.getYaw(pt));
		double pitch = Math.toRadians(player.getPitch(pt));
		double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
		// camera basis (forward, right, up) in Minecraft's axes
		double fx = -sy * cp, fy = -sp, fz = cy * cp;
		double rx = -cy, ry = 0.0, rz = -sy;
		double ux = ry * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - ry * fx;

		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		double aspect = (double) w / (double) h;
		double tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);

		for (Floater f : FLOATERS) {
			double dx = f.x - cam.x;
			double dy = f.y + (f.age + pt) * RISE_PER_TICK - cam.y;
			double dz = f.z - cam.z;
			double zc = dx * fx + dy * fy + dz * fz;
			if (zc < 0.2) continue; // behind the camera
			double xc = dx * rx + dy * ry + dz * rz;
			double yc = dx * ux + dy * uy + dz * uz;
			int sx = (int) (w / 2.0 * (1.0 + xc / (zc * tanHalf * aspect)));
			int sy2 = (int) (h / 2.0 * (1.0 - yc / (zc * tanHalf)));

			int alpha = f.age < LIFETIME - 8 ? 255 : MathHelper.clamp((LIFETIME - f.age) * 255 / 8, 12, 255);
			int color = (alpha << 24) | (f.rgb & 0xFFFFFF);
			ctx.drawTextWithShadow(mc.textRenderer, f.text, sx - mc.textRenderer.getWidth(f.text) / 2, sy2, color);
		}
	}
}
