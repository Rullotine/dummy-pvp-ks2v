package com.shubh.practicedummy.client;

import com.shubh.practicedummy.client.dummy.ArmorSet;
import com.shubh.practicedummy.client.dummy.DummyManager;
import com.shubh.practicedummy.client.dummy.DummyMode;
import com.shubh.practicedummy.client.dummy.DummySettings;
import com.shubh.practicedummy.client.dummy.DummyStats;
import com.shubh.practicedummy.client.dummy.HoldItem;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Settings screen: every button cycles / toggles its setting and updates its own label. */
public class DummyScreen extends Screen {
	private static final float[] HEALTH_PRESETS = {10.0F, 20.0F, 40.0F, 100.0F, 1000.0F};

	public DummyScreen() {
		super(Text.literal("Practice Dummy"));
	}

	@Override
	protected void init() {
		int colW = 150, gap = 8, rowH = 24;
		int left = this.width / 2 - colW - gap / 2;
		int right = this.width / 2 + gap / 2;
		int y = 36;

		MinecraftClient mc = MinecraftClient.getInstance();

		button(left, y, colW, () -> "Behaviour: " + DummySettings.mode.name().toLowerCase(Locale.ROOT),
			() -> DummyManager.setMode(mc, DummySettings.mode.next()));
		button(right, y, colW, () -> "Holds: " + DummySettings.hold.name().toLowerCase(Locale.ROOT),
			() -> DummyManager.setHold(mc, next(HoldItem.values(), DummySettings.hold)));
		y += rowH;

		button(left, y, colW, () -> "Armor: " + DummySettings.armor.name().toLowerCase(Locale.ROOT),
			() -> DummyManager.setArmor(mc, next(ArmorSet.values(), DummySettings.armor)));
		button(right, y, colW, () -> "Protection: " + DummySettings.protection,
			() -> DummySettings.protection = (DummySettings.protection + 1) % 5);
		y += rowH;

		button(left, y, colW, () -> "Dummy HP: " + (int) DummySettings.maxHealth,
			() -> DummyManager.setMaxHealth(mc, nextHealth()));
		button(right, y, colW, () -> "Stun time: " + String.format(Locale.ROOT, "%.1fs", DummySettings.stunTicks / 20.0F),
			() -> DummySettings.stunTicks = DummySettings.stunTicks >= 160 ? 40 : DummySettings.stunTicks + 20);
		y += rowH;

		button(left, y, colW, () -> "Wind Burst: " + DummySettings.windBurst,
			() -> DummySettings.windBurst = (DummySettings.windBurst + 1) % 4);
		button(right, y, colW, () -> "Boost (single-player): " + onOff(DummySettings.boost),
			() -> DummySettings.boost = !DummySettings.boost);
		y += rowH;

		button(left, y, colW, () -> "Boost looks forward: " + onOff(DummySettings.windLook),
			() -> DummySettings.windLook = !DummySettings.windLook);
		button(right, y, colW, () -> "Server boost (op): " + onOff(DummySettings.serverBoost),
			() -> DummySettings.serverBoost = !DummySettings.serverBoost);
		y += rowH;

		button(left, y, colW, () -> "Sim Breach: " + DummySettings.simBreach,
			() -> DummySettings.simBreach = (DummySettings.simBreach + 1) % 5);
		button(right, y, colW, () -> "Sim Density: " + DummySettings.simDensity,
			() -> DummySettings.simDensity = (DummySettings.simDensity + 1) % 6);
		y += rowH;

		button(left, y, colW, () -> "Sim Sharpness: " + DummySettings.simSharpness,
			() -> DummySettings.simSharpness = (DummySettings.simSharpness + 1) % 6);
		button(right, y, colW, () -> "HUD & bars: " + onOff(DummySettings.hud),
			() -> DummySettings.hud = !DummySettings.hud);
		y += rowH + 6;

		button(left, y, colW, () -> "Spawn at crosshair", () -> {
			close();
			DummyManager.spawnAtCrosshair(mc, null);
		});
		button(right, y, colW, () -> "Remove all dummies", () -> DummyManager.removeAll(mc));
		y += rowH;

		button(left, y, colW, () -> "Reset stats", DummyStats::reset);
		button(right, y, colW, () -> "Done", this::close);
	}

	private void button(int x, int y, int w, Supplier<String> label, Runnable action) {
		ButtonWidget[] holder = new ButtonWidget[1];
		holder[0] = ButtonWidget.builder(Text.literal(label.get()), b -> {
			action.run();
			b.setMessage(Text.literal(label.get()));
		}).dimensions(x, y, w, 20).build();
		addDrawableChild(holder[0]);
	}

	private static String onOff(boolean value) {
		return value ? "ON" : "OFF";
	}

	private static <T> T next(T[] values, T current) {
		for (int i = 0; i < values.length; i++) {
			if (values[i] == current) return values[(i + 1) % values.length];
		}
		return values[0];
	}

	private static float nextHealth() {
		for (int i = 0; i < HEALTH_PRESETS.length; i++) {
			if (HEALTH_PRESETS[i] > DummySettings.maxHealth + 0.01F) return HEALTH_PRESETS[i];
		}
		return HEALTH_PRESETS[0];
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,
			Text.literal("Practice Dummy"), this.width / 2, 14, 0xFFFFFFFF);
	}
}
