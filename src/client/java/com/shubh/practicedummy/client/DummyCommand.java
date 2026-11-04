package com.shubh.practicedummy.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.shubh.practicedummy.client.dummy.ArmorSet;
import com.shubh.practicedummy.client.dummy.DummyManager;
import com.shubh.practicedummy.client.dummy.DummyMode;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

/**
 * /dummy spawn [stand|walk|strafe]
 * /dummy remove
 * /dummy mode <stand|walk|strafe>
 * /dummy armor <none|iron|diamond|netherite>
 *
 * Client commands are handled locally and never sent to the server.
 */
final class DummyCommand {
	private DummyCommand() {}

	static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		LiteralArgumentBuilder<FabricClientCommandSource> spawn = ClientCommandManager.literal("spawn")
			.executes(ctx -> {
				DummyManager.spawnAtCrosshair(ctx.getSource().getClient(), null);
				return 1;
			});
		LiteralArgumentBuilder<FabricClientCommandSource> mode = ClientCommandManager.literal("mode");
		for (DummyMode m : DummyMode.values()) {
			String name = m.name().toLowerCase(Locale.ROOT);
			spawn.then(ClientCommandManager.literal(name).executes(ctx -> {
				DummyManager.spawnAtCrosshair(ctx.getSource().getClient(), m);
				return 1;
			}));
			mode.then(ClientCommandManager.literal(name).executes(ctx -> {
				DummyManager.setMode(ctx.getSource().getClient(), m);
				return 1;
			}));
		}

		LiteralArgumentBuilder<FabricClientCommandSource> armor = ClientCommandManager.literal("armor");
		for (ArmorSet a : ArmorSet.values()) {
			armor.then(ClientCommandManager.literal(a.name().toLowerCase(Locale.ROOT)).executes(ctx -> {
				DummyManager.setArmor(ctx.getSource().getClient(), a);
				return 1;
			}));
		}

		dispatcher.register(ClientCommandManager.literal("dummy")
			.then(spawn)
			.then(ClientCommandManager.literal("remove").executes(ctx -> {
				int n = DummyManager.removeAll(ctx.getSource().getClient());
				ctx.getSource().sendFeedback(Text.literal("Removed " + n + " dummy(s)"));
				return n;
			}))
			.then(mode)
			.then(armor));
	}
}
