package com.shubh.practicedummy.client.dummy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Everything drawn on the HUD: floating damage numbers, the health / stun bars over each dummy and
 * the stats panel. World points are projected to the screen with plain math (first person only),
 * which avoids touching the world renderer at all.
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

	/** Camera basis + projection for one frame. */
	private static final class Cam {
		double cx, cy, cz, fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalf, aspect;
		int w, h;

		/** Returns {screenX, screenY} or null if the point is behind the camera. */
		int[] project(double x, double y, double z) {
			double dx = x - cx, dy = y - cy, dz = z - cz;
			double zc = dx * fx + dy * fy + dz * fz;
			if (zc < 0.2) return null;
			double xc = dx * rx + dy * ry + dz * rz;
			double yc = dx * ux + dy * uy + dz * uz;
			return new int[] {
				(int) (w / 2.0 * (1.0 + xc / (zc * tanHalf * aspect))),
				(int) (h / 2.0 * (1.0 - yc / (zc * tanHalf)))
			};
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
		if (player == null || mc.options.hudHidden) return;

		if (mc.options.getPerspective().isFirstPerson() && (!FLOATERS.isEmpty() || !DummyManager.all().isEmpty())) {
			float pt = counter.getTickProgress(false);
			Cam cam = new Cam();
			Vec3d eye = player.getCameraPosVec(pt);
			cam.cx = eye.x;
			cam.cy = eye.y;
			cam.cz = eye.z;
			double yaw = Math.toRadians(player.getYaw(pt));
			double pitch = Math.toRadians(player.getPitch(pt));
			double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
			cam.fx = -sy * cp;
			cam.fy = -sp;
			cam.fz = cy * cp;
			cam.rx = -cy;
			cam.ry = 0.0;
			cam.rz = -sy;
			cam.ux = cam.ry * cam.fz - cam.rz * cam.fy;
			cam.uy = cam.rz * cam.fx - cam.rx * cam.fz;
			cam.uz = cam.rx * cam.fy - cam.ry * cam.fx;
			cam.w = ctx.getScaledWindowWidth();
			cam.h = ctx.getScaledWindowHeight();
			cam.aspect = (double) cam.w / (double) cam.h;
			cam.tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);

			drawFloaters(ctx, mc, cam, pt);
			if (DummySettings.hud) drawBars(ctx, mc, cam, pt);
		}
		if (DummySettings.hud) drawPanel(ctx, mc);
	}

	// ---------------------------------------------------------------- floaters
	private static void drawFloaters(DrawContext ctx, MinecraftClient mc, Cam cam, float pt) {
		for (Floater f : FLOATERS) {
			int[] p = cam.project(f.x, f.y + (f.age + pt) * RISE_PER_TICK, f.z);
			if (p == null) continue;
			int alpha = f.age < LIFETIME - 8 ? 255 : MathHelper.clamp((LIFETIME - f.age) * 255 / 8, 12, 255);
			int color = (alpha << 24) | (f.rgb & 0xFFFFFF);
			ctx.drawTextWithShadow(mc.textRenderer, f.text, p[0] - mc.textRenderer.getWidth(f.text) / 2, p[1], color);
		}
	}

	// ----------------------------------------------------- bars over each dummy
	private static void drawBars(DrawContext ctx, MinecraftClient mc, Cam cam, float pt) {
		ClientPlayerEntity player = mc.player;
		for (PracticeDummy d : DummyManager.all()) {
			if (player.squaredDistanceTo(d.getX(), d.getY(), d.getZ()) > 28.0 * 28.0) continue;
			double x = MathHelper.lerp(pt, d.lastX, d.getX());
			double y = MathHelper.lerp(pt, d.lastY, d.getY()) + d.getHeight() + 0.85;
			double z = MathHelper.lerp(pt, d.lastZ, d.getZ());
			int[] p = cam.project(x, y, z);
			if (p == null) continue;

			int bw = 44, bh = 5;
			int x0 = p[0] - bw / 2, y0 = p[1];
			float frac = MathHelper.clamp(d.getDummyHealth() / d.getDummyMaxHealth(), 0.0F, 1.0F);
			int color = frac > 0.5F ? 0xFF55FF55 : frac > 0.25F ? 0xFFFFFF55 : 0xFFFF5555;
			ctx.fill(x0 - 1, y0 - 1, x0 + bw + 1, y0 + bh + 1, 0xCC000000);
			ctx.fill(x0, y0, x0 + (int) (bw * frac), y0 + bh, color);
			if (d.getDummyAbsorption() > 0.0F) {
				float af = MathHelper.clamp(d.getDummyAbsorption() / d.getDummyMaxHealth(), 0.0F, 1.0F);
				ctx.fill(x0, y0, x0 + (int) (bw * af), y0 + 2, 0xFFFFC107);
			}

			String label;
			int labelColor = 0xFFFFFFFF;
			if (d.isStunned()) {
				label = String.format(Locale.ROOT, "STUNNED %.1fs", d.getStunTicks() / 20.0F);
				labelColor = 0xFF55FFFF;
				float sf = MathHelper.clamp((float) d.getStunTicks() / Math.max(1, DummySettings.stunTicks), 0.0F, 1.0F);
				ctx.fill(x0, y0 + bh + 2, x0 + (int) (bw * sf), y0 + bh + 4, 0xFF55FFFF);
			} else if (d.isShieldUp()) {
				label = "SHIELD UP";
				labelColor = 0xFF8888FF;
			} else {
				label = String.format(Locale.ROOT, "%.1f / %.0f", d.getDummyHealth(), d.getDummyMaxHealth());
			}
			ctx.drawTextWithShadow(mc.textRenderer, label, p[0] - mc.textRenderer.getWidth(label) / 2, y0 - 11, labelColor);
		}
	}

	// ---------------------------------------------------------------- panel
	private static void drawPanel(DrawContext ctx, MinecraftClient mc) {
		PracticeDummy d = DummyManager.focus(mc);
		List<String> lines = new ArrayList<>();
		lines.add("\u00a7l\u00a7bPractice Dummy \u00a7r\u00a77" + (d != null ? d.getMode().name().toLowerCase(Locale.ROOT) : "none out"));
		int barLine = -1;
		if (d != null) {
			barLine = lines.size();
			lines.add(""); // health bar goes here
			lines.add(String.format(Locale.ROOT, "\u00a7fHP %.1f/%.0f%s", d.getDummyHealth(), d.getDummyMaxHealth(),
				d.getDummyAbsorption() > 0.0F ? String.format(Locale.ROOT, " \u00a76+%.1f", d.getDummyAbsorption()) : ""));
			lines.add("\u00a7eTotem pops: \u00a7f" + d.getTotemPops() + " \u00a77(session " + DummyStats.totemPops + ")");
			lines.add("\u00a7bCombo: \u00a7fx" + d.getCombo() + " \u00a77(best " + DummyStats.bestCombo + ")");
			lines.add(String.format(Locale.ROOT, "\u00a7cLast: \u00a7f%.1f \u00a77DPS: \u00a7f%.1f", DummyStats.lastDamage, DummyStats.dps()));
			lines.add(String.format(Locale.ROOT, "\u00a77Total: %.0f dmg / %d hits", DummyStats.totalDamage, DummyStats.hits));
			if (d.getMode() == DummyMode.STUNPRACTICE || DummyStats.shieldBreaks > 0) {
				String shield = d.isStunned() ? String.format(Locale.ROOT, "\u00a7bSTUNNED %.1fs", d.getStunTicks() / 20.0F) : d.isShieldUp() ? "\u00a79SHIELD UP" : "\u00a77no shield";
				lines.add(shield);
				String best = DummyStats.bestStunReaction < 0 ? "-" : String.format(Locale.ROOT, "%.2fs", DummyStats.bestStunReaction / 20.0F);
				lines.add("\u00a7dStun slams: \u00a7f" + DummyStats.stunSlams + " \u00a77best " + best);
			}
		} else {
			lines.add("\u00a77Y = spawn, /dummy gui = settings");
		}

		int width = 0;
		for (String line : lines) width = Math.max(width, mc.textRenderer.getWidth(line));
		width = Math.max(width, 96);
		int x = 6, y = 6, lineH = 10;
		ctx.fill(x - 4, y - 4, x + width + 4, y + lines.size() * lineH + 2, 0x88000000);
		for (int i = 0; i < lines.size(); i++) {
			if (i == barLine && d != null) {
				float frac = MathHelper.clamp(d.getDummyHealth() / d.getDummyMaxHealth(), 0.0F, 1.0F);
				int color = frac > 0.5F ? 0xFF55FF55 : frac > 0.25F ? 0xFFFFFF55 : 0xFFFF5555;
				ctx.fill(x, y + i * lineH + 1, x + width, y + i * lineH + 7, 0xFF222222);
				ctx.fill(x, y + i * lineH + 1, x + (int) (width * frac), y + i * lineH + 7, color);
			} else {
				ctx.drawTextWithShadow(mc.textRenderer, lines.get(i), x, y + i * lineH, 0xFFFFFFFF);
			}
		}
	}
}
