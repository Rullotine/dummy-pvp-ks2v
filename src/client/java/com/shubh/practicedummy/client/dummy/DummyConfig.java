package com.shubh.practicedummy.client.dummy;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;

/** Saves your settings to config/practicedummy.properties, plus named presets. */
public final class DummyConfig {
	private static final Map<String, Supplier<String>> GET = new LinkedHashMap<>();
	private static final Map<String, Consumer<String>> SET = new LinkedHashMap<>();
	private static final Map<String, Map<String, String>> PRESETS = new LinkedHashMap<>();

	private static void reg(String key, Supplier<String> get, Consumer<String> set) {
		GET.put(key, get);
		SET.put(key, set);
	}

	static {
		reg("mode", () -> DummySettings.mode.name(), v -> DummySettings.mode = DummyMode.valueOf(v));
		reg("armor", () -> DummySettings.armor.name(), v -> DummySettings.armor = ArmorSet.valueOf(v));
		reg("hold", () -> DummySettings.hold.name(), v -> DummySettings.hold = HoldItem.valueOf(v));
		reg("maxHealth", () -> String.valueOf(DummySettings.maxHealth), v -> DummySettings.maxHealth = Float.parseFloat(v));
		reg("windBurst", () -> String.valueOf(DummySettings.windBurst), v -> DummySettings.windBurst = Integer.parseInt(v));
		reg("boost", () -> String.valueOf(DummySettings.boost), v -> DummySettings.boost = Boolean.parseBoolean(v));
		reg("swim", () -> String.valueOf(DummySettings.swim), v -> DummySettings.swim = Boolean.parseBoolean(v));
		reg("armorCalc", () -> String.valueOf(DummySettings.armorCalc), v -> DummySettings.armorCalc = Boolean.parseBoolean(v));
		reg("protection", () -> String.valueOf(DummySettings.protection), v -> DummySettings.protection = Integer.parseInt(v));
		reg("autoHeal", () -> String.valueOf(DummySettings.autoHeal), v -> DummySettings.autoHeal = Boolean.parseBoolean(v));
		reg("glint", () -> String.valueOf(DummySettings.glint), v -> DummySettings.glint = Boolean.parseBoolean(v));
		reg("hudBars", () -> String.valueOf(DummySettings.hudBars), v -> DummySettings.hudBars = Boolean.parseBoolean(v));
		reg("drill", () -> String.valueOf(DummySettings.drill), v -> DummySettings.drill = Boolean.parseBoolean(v));
		reg("hitback", () -> String.valueOf(DummySettings.hitback), v -> DummySettings.hitback = Boolean.parseBoolean(v));
		reg("wtap", () -> String.valueOf(DummySettings.wtap), v -> DummySettings.wtap = Boolean.parseBoolean(v));
		reg("pearl", () -> String.valueOf(DummySettings.pearl), v -> DummySettings.pearl = Boolean.parseBoolean(v));
		reg("windCharge", () -> String.valueOf(DummySettings.windCharge), v -> DummySettings.windCharge = Boolean.parseBoolean(v));
		reg("respawn", () -> String.valueOf(DummySettings.respawn), v -> DummySettings.respawn = Boolean.parseBoolean(v));
		reg("taunts", () -> String.valueOf(DummySettings.taunts), v -> DummySettings.taunts = Boolean.parseBoolean(v));
		reg("totems", () -> String.valueOf(DummySettings.totems), v -> DummySettings.totems = Integer.parseInt(v));
		reg("refill", () -> String.valueOf(DummySettings.refill), v -> DummySettings.refill = Integer.parseInt(v));
		reg("comboGoal", () -> String.valueOf(DummySettings.comboGoal), v -> DummySettings.comboGoal = Integer.parseInt(v));
		reg("sounds", () -> DummySettings.sounds, v -> DummySettings.sounds = v);
	}

	private DummyConfig() {}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("practicedummy.properties");
	}

	private static Map<String, String> snapshot() {
		Map<String, String> m = new LinkedHashMap<>();
		for (Map.Entry<String, Supplier<String>> e : GET.entrySet()) m.put(e.getKey(), e.getValue().get());
		return m;
	}

	private static void apply(Map<String, String> m) {
		for (Map.Entry<String, String> e : m.entrySet()) {
			Consumer<String> setter = SET.get(e.getKey());
			if (setter == null) continue;
			try {
				setter.accept(e.getValue());
			} catch (RuntimeException ignored) {
				// bad value in the file: keep the default
			}
		}
	}

	public static void load() {
		Path f = file();
		if (!Files.exists(f)) return;
		Properties props = new Properties();
		try (Reader r = Files.newBufferedReader(f)) {
			props.load(r);
		} catch (IOException e) {
			return;
		}
		Map<String, String> settings = new LinkedHashMap<>();
		for (String key : props.stringPropertyNames()) {
			if (key.startsWith("s.")) {
				settings.put(key.substring(2), props.getProperty(key));
			} else if (key.startsWith("p.")) {
				String rest = key.substring(2);
				int dot = rest.indexOf('.');
				if (dot > 0) PRESETS.computeIfAbsent(rest.substring(0, dot), k -> new LinkedHashMap<>())
					.put(rest.substring(dot + 1), props.getProperty(key));
			}
		}
		apply(settings);
	}

	public static void save() {
		Properties props = new Properties();
		for (Map.Entry<String, String> e : snapshot().entrySet()) props.setProperty("s." + e.getKey(), e.getValue());
		for (Map.Entry<String, Map<String, String>> p : PRESETS.entrySet()) {
			for (Map.Entry<String, String> e : p.getValue().entrySet()) props.setProperty("p." + p.getKey() + "." + e.getKey(), e.getValue());
		}
		try (Writer w = Files.newBufferedWriter(file())) {
			props.store(w, "Practice Dummy settings");
		} catch (IOException ignored) {
			// not fatal
		}
	}

	public static void savePreset(String name) {
		PRESETS.put(name.toLowerCase(), snapshot());
		save();
	}

	public static boolean loadPreset(String name) {
		Map<String, String> m = PRESETS.get(name.toLowerCase());
		if (m == null) return false;
		apply(m);
		for (PracticeDummy d : DummyManager.all()) {
			d.setMode(DummySettings.mode);
			d.resetTotems();
		}
		DummyManager.reapply(net.minecraft.client.MinecraftClient.getInstance());
		save();
		return true;
	}

	public static boolean deletePreset(String name) {
		boolean removed = PRESETS.remove(name.toLowerCase()) != null;
		if (removed) save();
		return removed;
	}

	public static List<String> presetNames() {
		return new ArrayList<>(PRESETS.keySet());
	}
}
