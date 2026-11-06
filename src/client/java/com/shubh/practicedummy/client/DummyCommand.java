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
import com.shubh.practicedummy.client.dummy.PracticeDummy;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

/**
 * /dummy spawn [stand|walk|strafe|fight]     /dummy remove
 * /dummy mode <...>      /dummy armor <none|iron|diamond|netherite>
 * /dummy hold <none|sword|axe|mace|spear|shield|bow>
 * /dummy name <text>     /dummy skin <online player|self>
 * /dummy health <hp>     /dummy windburst <auto|0-3>     /dummy boost <on|off>
 * /dummy gui   /dummy stats [reset]   /dummy protection <0-4>
 * /dummy swim|armorcalc|autoheal|hud|glint <on|off>
 * /dummy boost server <on|off>   (multiplayer: ask the server for real wind charges; needs op)
 *
 * Client commands are handled locally and never sent to the server.
 */
final class DummyCommand {
	private DummyCommand() {}

	private static LiteralArgumentBuilder<FabricClientCommandSource> lit(String name) {
		return ClientCommandManager.literal(name);
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> toggle(String name, String label,
		java.util.function.Consumer<Boolean> set) {
		return lit(name)
			.then(lit("on").executes(ctx -> {
				set.accept(true);
				ctx.getSource().sendFeedback(Text.literal(label + ": on"));
				return 1;
			}))
			.then(lit("off").executes(ctx -> {
				set.accept(false);
				ctx.getSource().sendFeedback(Text.literal(label + ": off"));
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
			.then(lit("windburst")
				.then(lit("auto").executes(ctx -> {
					DummySettings.windBurst = -1;
					ctx.getSource().sendFeedback(Text.literal("Wind Burst: auto (read from the mace you hold)"));
					return 1;
				}))
				.then(ClientCommandManager.argument("level", IntegerArgumentType.integer(0, 3))
				.executes(ctx -> {
					DummySettings.windBurst = IntegerArgumentType.getInteger(ctx, "level");
					ctx.getSource().sendFeedback(Text.literal("Wind Burst level forced to " + DummySettings.windBurst
						+ " (0 = just a small bounce). Only applies in single-player."));
					return 1;
				})))
			.then(lit("gui").executes(ctx -> {
				PracticeDummyClient.openGui = true;
				return 1;
			}))
			.then(lit("stats")
				.executes(ctx -> {
					PracticeDummy d = DummyManager.getFocus();
					if (d == null) {
						ctx.getSource().sendFeedback(Text.literal("No dummy to show stats for"));
						return 0;
					}
					String avg = d.getHits() == 0 ? "-" : String.format(Locale.ROOT, "%.1f", d.getTotalDamage() / d.getHits());
					ctx.getSource().sendFeedback(Text.literal(String.format(Locale.ROOT,
						"\u00a76Dummy stats: \u00a7fhits %d, total dmg %.1f, avg %s, best hit %.1f, crits %d, smashes %d (best %.1f), best combo %d, totem pops %d, shield blocks %d, stuns %d, stun slams %d",
						d.getHits(), d.getTotalDamage(), avg, d.getBestHit(), d.getCrits(), d.getSmashes(), d.getBestSmash(),
						d.getBestCombo(), d.getTotemPops(), d.getBlocks(), d.getStuns(), d.getStunSlams())));
					return 1;
				})
				.then(lit("reset").executes(ctx -> {
					for (PracticeDummy d : DummyManager.all()) d.resetStats();
					ctx.getSource().sendFeedback(Text.literal("Stats reset"));
					return 1;
				})))
			.then(toggle("swim", "Dummy swimming", v -> DummySettings.swim = v))
			.then(toggle("armorcalc", "Armor/Protection/Breach damage math", v -> DummySettings.armorCalc = v))
			.then(toggle("autoheal", "Dummy auto-heal", v -> DummySettings.autoHeal = v))
			.then(toggle("hud", "Status HUD", v -> DummySettings.hudBars = v))
			.then(toggle("glint", "Enchant glint", v -> {
				DummySettings.glint = v;
				DummyManager.reapply(net.minecraft.client.MinecraftClient.getInstance());
			}))
			.then(lit("protection").then(ClientCommandManager.argument("level", IntegerArgumentType.integer(0, 4))
				.executes(ctx -> {
					DummySettings.protection = IntegerArgumentType.getInteger(ctx, "level");
					ctx.getSource().sendFeedback(Text.literal("Dummy Protection level: " + DummySettings.protection));
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
				}))
				.then(lit("server")
					.then(lit("on").executes(ctx -> {
						DummySettings.serverBoost = true;
						ctx.getSource().sendFeedback(Text.literal("Server boost on: smashes will run /summon wind_charge (needs op on that server)"));
						return 1;
					}))
					.then(lit("off").executes(ctx -> {
						DummySettings.serverBoost = false;
						ctx.getSource().sendFeedback(Text.literal("Server boost off"));
						return 1;
					})))));
	}
}
