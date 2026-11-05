package com.shubh.practicedummy.client;

import com.shubh.practicedummy.client.dummy.DummyBoost;
import com.shubh.practicedummy.client.dummy.DummyHud;
import com.shubh.practicedummy.client.dummy.DummyManager;
import com.shubh.practicedummy.client.dummy.DummyStats;
import com.shubh.practicedummy.client.dummy.DummyTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
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

		KeyBinding guiKey = KeyBindingHelper.registerKeyBinding(
			new KeyBinding("key.practicedummy.gui", GLFW.GLFW_KEY_G, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			DummyManager.tick(client);
			DummyHud.tick();
			DummyStats.tick();
			DummyTracker.tick(client);
			DummyBoost.tick(client);
			if (DummyManager.openGuiRequested && client.currentScreen == null && client.player != null) {
				DummyManager.openGuiRequested = false;
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
