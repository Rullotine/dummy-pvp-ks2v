package com.shubh.practicedummy.client;

import com.shubh.practicedummy.client.dummy.ArmorSet;
import com.shubh.practicedummy.client.dummy.DummyManager;
import com.shubh.practicedummy.client.dummy.DummyMode;
import com.shubh.practicedummy.client.dummy.DummySettings;
import com.shubh.practicedummy.client.dummy.HoldItem;
import com.shubh.practicedummy.client.dummy.PracticeDummy;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Click-through settings screen (default key: G, or /dummy gui). */
public class DummyScreen extends Screen {
	private static final float[] HEALTH_PRESETS = {10.0F, 20.0F, 40.0F, 100.0F, 500.0F};

	public DummyScreen() {
		super(Text.literal("Practice Dummy"));
	}

	@Override
	protected void init() {
		int cw = 150, gap = 6, step = 24;
		int x1 = this.width / 2 - cw - gap / 2;
		int x2 = this.width / 2 + gap / 2;
		int y = 34;

		cycle(x1, y, "Mode", () -> DummySettings.mode.name().toLowerCase(Locale.ROOT),
			() -> DummyManager.cycleMode(this.client));
		cycle(x2, y, "Armor", () -> DummySettings.armor.name().toLowerCase(Locale.ROOT), () -> {
			ArmorSet[] v = ArmorSet.values();
			DummyManager.setArmor(this.client, v[(DummySettings.armor.ordinal() + 1) % v.length]);
		});
		y += step;
		cycle(x1, y, "Holds", () -> DummySettings.hold.name().toLowerCase(Locale.ROOT), () -> {
			HoldItem[] v = HoldItem.values();
			DummyManager.setHold(this.client, v[(DummySettings.hold.ordinal() + 1) % v.length]);
		});
		cycle(x2, y, "Protection", () -> String.valueOf(DummySettings.protection),
			() -> DummySettings.protection = (DummySettings.protection + 1) % 5);
		y += step;
		cycle(x1, y, "Wind Burst", () -> DummySettings.windBurst < 0 ? "auto" : String.valueOf(DummySettings.windBurst),
			() -> DummySettings.windBurst = DummySettings.windBurst >= 3 ? -1 : DummySettings.windBurst + 1);
		cycle(x2, y, "Mace boost", () -> onOff(DummySettings.boost), () -> DummySettings.boost = !DummySettings.boost);
		y += step;
		cycle(x1, y, "Swimming", () -> onOff(DummySettings.swim), () -> DummySettings.swim = !DummySettings.swim);
		cycle(x2, y, "Armor math", () -> onOff(DummySettings.armorCalc), () -> DummySettings.armorCalc = !DummySettings.armorCalc);
		y += step;
		cycle(x1, y, "Auto-heal", () -> onOff(DummySettings.autoHeal), () -> DummySettings.autoHeal = !DummySettings.autoHeal);
		cycle(x2, y, "Glint", () -> onOff(DummySettings.glint), () -> {
			DummySettings.glint = !DummySettings.glint;
			DummyManager.reapply(this.client);
		});
		y += step;
		cycle(x1, y, "Status HUD", () -> onOff(DummySettings.hudBars), () -> DummySettings.hudBars = !DummySettings.hudBars);
		cycle(x2, y, "Health", () -> String.format(Locale.ROOT, "%.0f", DummySettings.maxHealth), () -> {
			float next = HEALTH_PRESETS[0];
			for (float p : HEALTH_PRESETS) {
				if (p > DummySettings.maxHealth + 0.01F) {
					next = p;
					break;
				}
			}
			DummyManager.setMaxHealth(this.client, next);
		});
		y += step + 6;
		action(x1, y, "Spawn at crosshair", () -> {
			close();
			DummyManager.spawnAtCrosshair(this.client, null);
		});
		action(x2, y, "Remove all", () -> DummyManager.removeAll(this.client));
		y += step;
		action(x1, y, "Reset stats", () -> {
			for (PracticeDummy d : DummyManager.all()) d.resetStats();
		});
		action(x2, y, "Done", this::close);
	}

	private static String onOff(boolean v) {
		return v ? "on" : "off";
	}

	private void cycle(int x, int y, String label, Supplier<String> value, Runnable onClick) {
		addDrawableChild(ButtonWidget.builder(Text.literal(label + ": " + value.get()), b -> {
			onClick.run();
			b.setMessage(Text.literal(label + ": " + value.get()));
		}).dimensions(x, y, 150, 20).build());
	}

	private void action(int x, int y, String label, Runnable onClick) {
		addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> onClick.run())
			.dimensions(x, y, 150, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		String title = "Practice Dummy";
		context.drawTextWithShadow(this.textRenderer, title, this.width / 2 - this.textRenderer.getWidth(title) / 2, 16, 0xFFFFFFFF);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
