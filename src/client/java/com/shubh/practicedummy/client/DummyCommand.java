package com.shubh.practicedummy.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.shubh.practicedummy.client.dummy.ArmorSet;
import com.shubh.practicedummy.client.dummy.DummyManager;
import com.shubh.practicedummy.client.dummy.DummyMode;
import com.shubh.practicedummy.client.dummy.DummySettings;
import com.shubh.practicedummy.client.dummy.HoldItem;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

/**
 * /dummy spawn [stand|walk|strafe|fight]     /dummy remove
 * /dummy mode <...>      /dummy armor <none|iron|diamond|netherite>
 * /dummy hold <none|sword|axe|mace|spear|shield>
 * /dummy name <text>     /dummy skin <online player|self>
 * /dummy health <hp>     /dummy windburst <0-3>     /dummy boost <on|off>
 *
 * Client commands are handled locally and never sent to the server.
 */
final class DummyCommand {
	private DummyCommand() {}

	private static LiteralArgumentBuilder<FabricClientCommandSource> lit(String name) {
		return ClientCommandManager.literal(name);
	}

	static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		LiteralArgumentBuilder<FabricClientCommandSource> spawn = lit("spawn").executes(ctx -> {
			DummyManager.spawnAtCrosshair(ctx.getSource().getClient(), null);
			return 1;
		});
		LiteralArgumentBuilder<FabricClientCommandSource> mode = lit("mode");
		for (DummyMode m : DummyMode.values()) {
			String name = m.name().toLowerCase(Locale.ROOT);
			spawn.then(lit(name).executes(ctx -> {
				DummyManager.spawnAtCrosshair(ctx.getSource().getClient(), m);
				return 1;
			}));
			mode.then(lit(name).executes(ctx -> {
				DummyManager.setMode(ctx.getSource().getClient(), m);
				return 1;
			}));
		}

		LiteralArgumentBuilder<FabricClientCommandSource> armor = lit("armor");
		for (ArmorSet a : ArmorSet.values()) {
			armor.then(lit(a.name().toLowerCase(Locale.ROOT)).executes(ctx -> {
				DummyManager.setArmor(ctx.getSource().getClient(), a);
				return 1;
			}));
		}

		LiteralArgumentBuilder<FabricClientCommandSource> hold = lit("hold");
		for (HoldItem h : HoldItem.values()) {
			hold.then(lit(h.name().toLowerCase(Locale.ROOT)).executes(ctx -> {
				DummyManager.setHold(ctx.getSource().getClient(), h);
				return 1;
			}));
		}

		dispatcher.register(lit("dummy")
			.then(spawn)
			.then(lit("remove").executes(ctx -> {
				int n = DummyManager.removeAll(ctx.getSource().getClient());
				ctx.getSource().sendFeedback(Text.literal("Removed " + n + " dummy(s)"));
				return n;
			}))
			.then(mode)
			.then(armor)
			.then(hold)
			.then(lit("name").then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
				.executes(ctx -> {
					DummyManager.setName(ctx.getSource().getClient(), StringArgumentType.getString(ctx, "name"));
					return 1;
				})))
			.then(lit("skin").then(ClientCommandManager.argument("player", StringArgumentType.word())
				.executes(ctx -> {
					String who = StringArgumentType.getString(ctx, "player");
					boolean ok = DummyManager.setSkin(ctx.getSource().getClient(), who);
					ctx.getSource().sendFeedback(Text.literal(ok
						? "Dummy skin: " + who
						: "\u00a7c" + who + " isn't in the tab list (use their exact name), or type 'self'"));
					return ok ? 1 : 0;
				})))
			.then(lit("health").then(ClientCommandManager.argument("hp", FloatArgumentType.floatArg(1.0F, 1000.0F))
				.executes(ctx -> {
					DummyManager.setMaxHealth(ctx.getSource().getClient(), FloatArgumentType.getFloat(ctx, "hp"));
					return 1;
				})))
			.then(lit("windburst").then(ClientCommandManager.argument("level", IntegerArgumentType.integer(0, 3))
				.executes(ctx -> {
					DummySettings.windBurst = IntegerArgumentType.getInteger(ctx, "level");
					ctx.getSource().sendFeedback(Text.literal("Wind Burst level: " + DummySettings.windBurst
						+ " (0 = just a small bounce). Only applies in single-player."));
					return 1;
				})))
			.then(lit("boost")
				.then(lit("on").executes(ctx -> {
					DummySettings.boost = true;
					ctx.getSource().sendFeedback(Text.literal("Mace boost on (single-player only)"));
					return 1;
				}))
				.then(lit("off").executes(ctx -> {
					DummySettings.boost = false;
					ctx.getSource().sendFeedback(Text.literal("Mace boost off"));
					return 1;
				}))));
	}
}
