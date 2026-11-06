package com.shubh.practicedummy.client;

import com.shubh.practicedummy.client.dummy.DummyBoost;
import com.shubh.practicedummy.client.dummy.DummyCombat;
import com.shubh.practicedummy.client.dummy.DummyConfig;
import com.shubh.practicedummy.client.dummy.DummySettings;
import com.shubh.practicedummy.client.dummy.DummyTraining;
import com.shubh.practicedummy.client.dummy.DummyProjectiles;
import com.shubh.practicedummy.client.dummy.DummyHud;
import com.shubh.practicedummy.client.dummy.DummyManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class PracticeDummyClient implements ClientModInitializer {
	public static final String MOD_ID = "practicedummy";
	/** Set by /dummy gui; the screen is opened on the next tick so the chat screen doesn't close it. */
	public static boolean openGui;

	private static void flash(net.minecraft.client.MinecraftClient client, String msg) {
		if (client.inGameHud != null) client.inGameHud.setOverlayMessage(Text.literal("\u00a7a" + msg), false);
	}

	@Override
	@SuppressWarnings("deprecation") // HudRenderCallback still works; it just has a newer replacement
	public void onInitializeClient() {
		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
		KeyBinding spawnKey = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.practicedummy.spawn", GLFW.GLFW_KEY_Y, category));
		KeyBinding removeKey = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.practicedummy.remove", GLFW.GLFW_KEY_U, category));
		KeyBinding modeKey = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.practicedummy.mode", GLFW.GLFW_KEY_I, category));

		KeyBinding hudKey = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.practicedummy.hud", GLFW.GLFW_KEY_H, category));
		KeyBinding challengeKey = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.practicedummy.challenge", GLFW.GLFW_KEY_J, category));
		KeyBinding drillKey = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.practicedummy.drill", GLFW.GLFW_KEY_K, category));
		DummyConfig.load();
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> DummyConfig.save());

		KeyBinding guiKey = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.practicedummy.gui", GLFW.GLFW_KEY_G, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			DummyManager.tick(client);
			DummyHud.tick();
			DummyBoost.tick(client);
			DummyCombat.tick(client);
			DummyProjectiles.tick(client);
			DummyTraining.tick();
			while (hudKey.wasPressed()) {
				DummySettings.hudBars = !DummySettings.hudBars;
				flash(client, "Status HUD: " + (DummySettings.hudBars ? "on" : "off"));
			}
			while (drillKey.wasPressed()) {
				DummySettings.drill = !DummySettings.drill;
				flash(client, "Reaction drill: " + (DummySettings.drill ? "on" : "off"));
			}
			while (challengeKey.wasPressed()) {
				if (DummyTraining.isChallengeActive()) DummyTraining.stopChallenge();
				else DummyTraining.startChallenge(30);
			}
			if (openGui) {
				openGui = false;
				client.setScreen(new DummyScreen());
			}
			while (guiKey.wasPressed()) client.setScreen(new DummyScreen());
			while (spawnKey.wasPressed()) DummyManager.spawnAtCrosshair(client, null);
			while (removeKey.wasPressed()) {
				int removed = DummyManager.removeAll(client);
				if (client.inGameHud != null && removed > 0) {
					client.inGameHud.setOverlayMessage(Text.literal("\u00a7aRemoved " + removed + " dummy(s)"), false);
				}
			}
			while (modeKey.wasPressed()) DummyManager.cycleMode(client);
		});

		HudRenderCallback.EVENT.register(DummyHud::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> DummyManager.clear());
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> DummyCommand.register(dispatcher));
	}
}
