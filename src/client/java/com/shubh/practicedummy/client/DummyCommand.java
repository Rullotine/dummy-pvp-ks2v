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
import com.shubh.practicedummy.client.dummy.DummyStats;
import com.shubh.practicedummy.client.dummy.HoldItem;
import java.util.Locale;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

/**
 * /dummy spawn [mode]       /dummy remove          /dummy gui
 * /dummy mode <stand|walk|strafe|fight|stunpractice>
 * /dummy armor <none|iron|diamond|netherite>       /dummy protection <0-4>
 * /dummy hold <none|sword|axe|mace|spear|shield>
 * /dummy name <text>        /dummy skin <online player|self>      /dummy health <hp>
 * /dummy enchant <breach 0-4|density 0-5|sharpness 0-5> <level>   (acts as if your weapon had it)
 * /dummy windburst <0-3>    /dummy windburst look <on|off>
 * /dummy boost <on|off>     /dummy boost server <on|off>
 * /dummy stun <seconds>     /dummy hud <on|off>    /dummy stats reset
 *
 * Client commands are handled locally and never sent to the server.
 */
final class DummyCommand {
	private DummyCommand() {}

	private static LiteralArgumentBuilder<FabricClientCommandSource> lit(String name) {
		return ClientCommandManager.literal(name);
	}

	private static void say(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx, String message) {
		ctx.getSource().sendFeedback(Text.literal(message));
	}

	/** literal(name) -> on / off, calling the consumer. */
	private static LiteralArgumentBuilder<FabricClientCommandSource> toggle(String name, String what, Consumer<Boolean> setter) {
		return lit(name)
			.then(lit("on").executes(ctx -> {
				setter.accept(true);
				say(ctx, what + ": ON");
				return 1;
			}))
			.then(lit("off").executes(ctx -> {
				setter.accept(false);
				say(ctx, what + ": OFF");
				return 1;
			}));
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

		LiteralArgumentBuilder<FabricClientCommandSource> enchant = lit("enchant")
			.then(lit("breach").then(ClientCommandManager.argument("level", IntegerArgumentType.integer(0, 4))
				.executes(ctx -> {
					DummySettings.simBreach = IntegerArgumentType.getInteger(ctx, "level");
					say(ctx, "Simulated Breach: " + DummySettings.simBreach + " (your real enchant counts too, the higher wins)");
					return 1;
				})))
			.then(lit("density").then(ClientCommandManager.argument("level", IntegerArgumentType.integer(0, 5))
				.executes(ctx -> {
					DummySettings.simDensity = IntegerArgumentType.getInteger(ctx, "level");
					say(ctx, "Simulated Density: " + DummySettings.simDensity);
					return 1;
				})))
			.then(lit("sharpness").then(ClientCommandManager.argument("level", IntegerArgumentType.integer(0, 5))
				.executes(ctx -> {
					DummySettings.simSharpness = IntegerArgumentType.getInteger(ctx, "level");
					say(ctx, "Simulated Sharpness: " + DummySettings.simSharpness);
					return 1;
				})));

		dispatcher.register(lit("dummy")
			.then(spawn)
			.then(lit("remove").executes(ctx -> {
				int n = DummyManager.removeAll(ctx.getSource().getClient());
				say(ctx, "Removed " + n + " dummy(s)");
				return n;
			}))
			.then(lit("gui").executes(ctx -> {
				DummyManager.openGuiRequested = true;
				return 1;
			}))
			.then(mode)
			.then(armor)
			.then(hold)
			.then(enchant)
			.then(lit("protection").then(ClientCommandManager.argument("level", IntegerArgumentType.integer(0, 4))
				.executes(ctx -> {
					DummySettings.protection = IntegerArgumentType.getInteger(ctx, "level");
					say(ctx, "Dummy armor Protection: " + DummySettings.protection + " (per piece)");
					return 1;
				})))
			.then(lit("name").then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
				.executes(ctx -> {
					DummyManager.setName(ctx.getSource().getClient(), StringArgumentType.getString(ctx, "name"));
					return 1;
				})))
			.then(lit("skin").then(ClientCommandManager.argument("player", StringArgumentType.word())
				.executes(ctx -> {
					String who = StringArgumentType.getString(ctx, "player");
					boolean ok = DummyManager.setSkin(ctx.getSource().getClient(), who);
					say(ctx, ok ? "Dummy skin: " + who
						: "\u00a7c" + who + " isn't in the tab list (use their exact name), or type 'self'");
					return ok ? 1 : 0;
				})))
			.then(lit("health").then(ClientCommandManager.argument("hp", FloatArgumentType.floatArg(1.0F, 1000.0F))
				.executes(ctx -> {
					DummyManager.setMaxHealth(ctx.getSource().getClient(), FloatArgumentType.getFloat(ctx, "hp"));
					return 1;
				})))
			.then(lit("windburst")
				.then(ClientCommandManager.argument("level", IntegerArgumentType.integer(0, 3))
					.executes(ctx -> {
						DummySettings.windBurst = IntegerArgumentType.getInteger(ctx, "level");
						say(ctx, "Wind Burst level: " + DummySettings.windBurst + " (0 = small bounce; a real Wind Burst mace overrides this)");
						return 1;
					}))
				.then(toggle("look", "Boost also kicks you forward", v -> DummySettings.windLook = v)))
			.then(lit("boost")
				.then(lit("on").executes(ctx -> {
					DummySettings.boost = true;
					say(ctx, "Mace boost on (direct launch is single-player only)");
					return 1;
				}))
				.then(lit("off").executes(ctx -> {
					DummySettings.boost = false;
					say(ctx, "Mace boost off");
					return 1;
				}))
				.then(toggle("server", "Server boost (/summon wind_charge, needs op)", v -> DummySettings.serverBoost = v)))
			.then(lit("stun").then(ClientCommandManager.argument("seconds", FloatArgumentType.floatArg(0.5F, 30.0F))
				.executes(ctx -> {
					DummySettings.stunTicks = Math.round(FloatArgumentType.getFloat(ctx, "seconds") * 20.0F);
					say(ctx, "Shield stun length: " + DummySettings.stunTicks / 20.0F + "s (vanilla axe = 5s)");
					return 1;
				})))
			.then(toggle("hud", "Stats panel and bars", v -> DummySettings.hud = v))
			.then(lit("stats").then(lit("reset").executes(ctx -> {
				DummyStats.reset();
				say(ctx, "Stats reset");
				return 1;
			}))));
	}
}
