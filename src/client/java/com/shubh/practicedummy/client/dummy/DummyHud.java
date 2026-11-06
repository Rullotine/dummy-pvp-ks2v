package com.shubh.practicedummy.client.dummy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Floating damage numbers, the status panel, totem-pop banner, screen flashes and the smash meter. */
public final class DummyHud {
	private static final int LIFETIME = 26;
	private static final double RISE_PER_TICK = 0.04;
	private static final List<Floater> FLOATERS = new ArrayList<>();
	private static ItemStack totemIcon;

	private static int bannerTicks;
	private static String bannerTitle = "", bannerSub = "";
	private static int bannerRgb = 0xFFD700;
	private static int flashTicks;
	private static int flashRgb = 0xFF0000;

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

	public static void popBanner(String title, String sub, int rgb) {
		bannerTitle = title;
		bannerSub = sub;
		bannerRgb = rgb;
		bannerTicks = 40;
		if (rgb == 0xFFD700) flash(0xFFD700);
	}

	public static void flash(int rgb) {
		flashRgb = rgb;
		flashTicks = 8;
	}

	public static void clear() {
		FLOATERS.clear();
		bannerTicks = 0;
		flashTicks = 0;
	}

	public static void tick() {
		for (Floater f : FLOATERS) f.age++;
		FLOATERS.removeIf(f -> f.age > LIFETIME);
		if (bannerTicks > 0) bannerTicks--;
		if (flashTicks > 0) flashTicks--;
	}

	public static void render(DrawContext ctx, RenderTickCounter counter) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientPlayerEntity player = mc.player;
		if (player == null || mc.options.hudHidden) return;
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();

		if (flashTicks > 0) {
			int alpha = Math.min(60, flashTicks * 9);
			ctx.fill(0, 0, w, h, (alpha << 24) | (flashRgb & 0xFFFFFF));
		}
		if (DummySettings.hudBars) {
			PracticeDummy focus = DummyManager.getFocus();
			if (focus != null) panel(ctx, mc, focus, w);
		}
		PracticeDummy cueDummy = DummyManager.getFocus();
		if (cueDummy != null && cueDummy.isDrillOpen()) {
			String cue = "\u00a7a\u00a7lHIT NOW!";
			ctx.drawTextWithShadow(mc.textRenderer, cue, w / 2 - mc.textRenderer.getWidth(cue) / 2, h / 2 - 28, 0xFFFFFFFF);
		}
		smashMeter(ctx, mc, player, w, h);
		if (bannerTicks > 0) {
			int a = bannerTicks > 10 ? 255 : bannerTicks * 255 / 10;
			int c = (a << 24) | (bannerRgb & 0xFFFFFF);
			int y = h / 3;
			ctx.drawTextWithShadow(mc.textRenderer, bannerTitle, w / 2 - mc.textRenderer.getWidth(bannerTitle) / 2, y, c);
			ctx.drawTextWithShadow(mc.textRenderer, bannerSub, w / 2 - mc.textRenderer.getWidth(bannerSub) / 2, y + 11,
				(a << 24) | 0xFFFFFF);
		}
		floaters(ctx, mc, player, counter, w, h);
	}

	private static void panel(DrawContext ctx, MinecraftClient mc, PracticeDummy d, int w) {
		List<String> lines = new ArrayList<>();
		lines.add(String.format(Locale.ROOT, "\u00a77DPS \u00a7f%.1f \u00a78| \u00a77hits \u00a7f%d \u00a78| \u00a77combo \u00a7f%d\u00a77 (best %d)",
			d.getDps(), d.getHits(), d.getCombo(), d.getBestCombo()));
		int melee = DummyTraining.getMeleeHits();
		int sprint = DummyTraining.getSprintHits();
		int critPct = d.getHits() == 0 ? 0 : d.getCrits() * 100 / d.getHits();
		lines.add(String.format(Locale.ROOT, "\u00a77sprint \u00a7f%d/%d (%d%%) \u00a78| \u00a77crit \u00a7f%d%% \u00a78| \u00a77kills \u00a7f%d \u00a77(x%d)",
			sprint, melee, melee == 0 ? 0 : sprint * 100 / melee, critPct, DummyTraining.getKills(), DummyTraining.getStreak()));
		boolean shieldBar = false;
		if (d.hasShield() || d.isShieldDisabled()) {
			String best = d.getBestReaction() < 0 ? "-" : String.format(Locale.ROOT, "%.2fs", d.getBestReaction() / 20.0F);
			String st;
			if (d.isShieldDisabled()) {
				st = String.format(Locale.ROOT, "\u00a76STUNNED \u00a7f%.1fs", d.getShieldDisabledTicks() / 20.0F);
				shieldBar = true;
			} else {
				st = "\u00a7bSHIELD UP";
			}
			lines.add(st + " \u00a78| \u00a77slams \u00a7f" + d.getStunSlams() + "\u00a77/" + d.getStuns() + " \u00a78| \u00a77best \u00a7f" + best);
		}
		if (DummySettings.drill) {
			float avg = DummyTraining.getAvgReactionSeconds();
			lines.add(String.format(Locale.ROOT, "\u00a7bDRILL \u00a77avg \u00a7f%s \u00a78| \u00a77best \u00a7f%s \u00a78| \u00a77missed \u00a7f%d",
				avg < 0 ? "-" : String.format(Locale.ROOT, "%.2fs", avg),
				DummyTraining.getBestReaction() < 0 ? "-" : String.format(Locale.ROOT, "%.2fs", DummyTraining.getBestReaction() / 20.0F),
				DummyTraining.getMissedCues()));
		}
		if (DummyTraining.isChallengeActive()) {
			lines.add(String.format(Locale.ROOT, "\u00a7aCHALLENGE \u00a7f%.1fs \u00a78| \u00a77hits \u00a7f%d \u00a78| \u00a77dmg \u00a7f%.1f \u00a78| \u00a77best \u00a7f%.1f",
				DummyTraining.getChallengeSecondsLeft(), DummyTraining.getChallengeHits(), DummyTraining.getChallengeDamage(),
				DummyTraining.getBestChallengeDamage()));
		}
		if (DummySettings.comboGoal > 0) {
			lines.add("\u00a7eCombo goal x" + DummySettings.comboGoal + " \u00a78| \u00a77done \u00a7f" + DummyTraining.getComboGoalsDone());
		}

		int pw = 220;
		int py = 6;
		int px = (w - pw) / 2;
		int by = py + 12;
		int ty = by + 17;
		int ly = ty + 15;
		int ph = (ly - py) + lines.size() * 12 + 4;
		ctx.fill(px - 3, py - 3, px + pw + 3, py + ph, 0x90000000);

		String head = "\u00a7f" + d.getName().getString() + " \u00a78| \u00a77" + d.getMode().name().toLowerCase(Locale.ROOT)
			+ (d.isDead() ? " \u00a74DEAD" : "");
		ctx.drawTextWithShadow(mc.textRenderer, head, px, py, 0xFFFFFFFF);

		float max = d.getDummyMaxHealth();
		ctx.fill(px, by, px + pw, by + 10, 0xFF2A2A2A);
		float frac = MathHelper.clamp(d.getDummyHealth() / max, 0.0F, 1.0F);
		int col = frac > 0.5F ? 0xFF3CC03C : frac > 0.25F ? 0xFFE0B020 : 0xFFE03030;
		ctx.fill(px, by, px + (int) (pw * frac), by + 10, col);
		String hp = String.format(Locale.ROOT, "%.1f / %.0f", Math.max(0.0F, d.getDummyHealth()), max);
		ctx.drawTextWithShadow(mc.textRenderer, hp, px + pw / 2 - mc.textRenderer.getWidth(hp) / 2, by + 1, 0xFFFFFFFF);
		int aw = (int) (pw * MathHelper.clamp(d.getDummyAbsorption() / max, 0.0F, 1.0F));
		ctx.fill(px, by + 11, px + pw, by + 14, 0xFF2A2A2A);
		if (aw > 0) ctx.fill(px, by + 11, px + aw, by + 14, 0xFFFFC107);

		if (totemIcon == null) totemIcon = new ItemStack(Items.TOTEM_OF_UNDYING);
		ctx.drawItem(totemIcon, px, ty - 3);
		String left = d.getTotemsLeft() < 0 ? "inf" : String.valueOf(d.getTotemsLeft());
		String pops = "\u00a76x" + d.getTotemPops() + " \u00a77pops \u00a78| \u00a77left \u00a7f" + left;
		if (d.getLastPopSeconds() >= 0.0F) {
			pops += String.format(Locale.ROOT, " \u00a78| \u00a77last \u00a7f%.2fs\u00a77/\u00a7f%d", d.getLastPopSeconds(), d.getLastPopHits());
		}
		ctx.drawTextWithShadow(mc.textRenderer, pops, px + 20, ty + 1, 0xFFFFFFFF);

		int y = ly;
		for (String line : lines) {
			ctx.drawTextWithShadow(mc.textRenderer, line, px, y, 0xFFFFFFFF);
			y += 12;
		}
		if (shieldBar) {
			int sw = pw * d.getShieldDisabledTicks() / 100;
			int sy = ly + 24 + 9;
			ctx.fill(px, sy, px + pw, sy + 2, 0xFF2A2A2A);
			ctx.fill(px, sy, px + Math.min(pw, sw), sy + 2, 0xFF55FFFF);
		}
	}

	private static void smashMeter(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity p, int w, int h) {
		if (DummyManager.all().isEmpty() && DummyBoost.getHudTicks() <= 0) return;
		int y = h / 2 + 18;
		if (p.getMainHandStack().isOf(Items.MACE) && !p.isOnGround() && p.fallDistance > 0.5) {
			double fall = p.fallDistance;
			boolean ready = fall > 1.5;
			String t = String.format(Locale.ROOT, "%s%.1fm fall  +%.1f smash dmg", ready ? "\u00a76" : "\u00a77", fall,
				ready ? DummyCombat.smashBonus(fall) : 0.0);
			ctx.drawTextWithShadow(mc.textRenderer, t, w / 2 - mc.textRenderer.getWidth(t) / 2, y, 0xFFFFFFFF);
			String lv = "\u00a78" + DummyBoost.levelLabel(p);
			ctx.drawTextWithShadow(mc.textRenderer, lv, w / 2 - mc.textRenderer.getWidth(lv) / 2, y + 10, 0xFFFFFFFF);
		} else if (!p.getMainHandStack().isOf(Items.MACE) && !p.isOnGround() && p.fallDistance > 0.0 && !DummyManager.all().isEmpty()) {
			String t = p.isSprinting() ? "\u00a77crit: stop sprinting"
				: p.getAttackCooldownProgress(0.5F) < 0.9F ? "\u00a77crit: wait for the charge" : "\u00a7aCRIT READY";
			ctx.drawTextWithShadow(mc.textRenderer, t, w / 2 - mc.textRenderer.getWidth(t) / 2, y, 0xFFFFFFFF);
		} else if (DummyBoost.getHudTicks() > 0) {
			String t = "\u00a7bBoost +" + DummyBoost.heightText() + " blocks"
				+ (DummyBoost.getChain() > 1 ? " \u00a7dchain x" + DummyBoost.getChain() : "");
			ctx.drawTextWithShadow(mc.textRenderer, t, w / 2 - mc.textRenderer.getWidth(t) / 2, y, 0xFFFFFFFF);
		}
	}

	private static void floaters(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity player, RenderTickCounter counter,
		int w, int h) {
		if (FLOATERS.isEmpty() || !mc.options.getPerspective().isFirstPerson()) return;
		float pt = counter.getTickProgress(false);
		Vec3d cam = player.getCameraPosVec(pt);
		double yaw = Math.toRadians(player.getYaw(pt));
		double pitch = Math.toRadians(player.getPitch(pt));
		double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pitch), sp = Math.sin(pitch);
		double fx = -sy * cp, fy = -sp, fz = cy * cp;
		double rx = -cy, ry = 0.0, rz = -sy;
		double ux = ry * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - ry * fx;
		double aspect = (double) w / (double) h;
		double tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);

		for (Floater f : FLOATERS) {
			double dx = f.x - cam.x;
			double dy = f.y + (f.age + pt) * RISE_PER_TICK - cam.y;
			double dz = f.z - cam.z;
			double zc = dx * fx + dy * fy + dz * fz;
			if (zc < 0.2) continue;
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
