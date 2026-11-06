package dev.xingguang.terminalblade.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import dev.xingguang.terminalblade.TerminalBladeConfig;
import dev.xingguang.terminalblade.combat.MistletoeFormulas;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import static dev.xingguang.terminalblade.blade.MistletoeBlade.NAME;

@EventBusSubscriber(modid = dev.xingguang.terminalblade.TerminalBlade.MOD_ID)
public final class MistletoeCommands {
    private MistletoeCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var give = Commands.literal("give")
                .executes(ctx -> give(ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> give(EntityArgument.getPlayer(ctx, "player"))));
        var audit = Commands.literal("audit")
                .then(Commands.argument("target_max", FloatArgumentType.floatArg(0.0F))
                        .then(Commands.argument("player_current", FloatArgumentType.floatArg(0.0F))
                                .then(Commands.argument("player_max", FloatArgumentType.floatArg(0.1F))
                                        .executes(ctx -> audit(
                                                ctx.getSource(),
                                                FloatArgumentType.getFloat(ctx, "target_max"),
                                                FloatArgumentType.getFloat(ctx, "player_current"),
                                                FloatArgumentType.getFloat(ctx, "player_max"))))));
        event.getDispatcher().register(Commands.literal("mistletoe")
                .requires(source -> source.hasPermission(2))
                .then(give)
                .then(audit));
    }

    private static int give(ServerPlayer player) {
        ResourceKey<SlashBladeDefinition> key = ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY, NAME);
        ItemStack blade = player.server.registryAccess()
                .lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY)
                .getOrThrow(key)
                .value()
                .getBlade(player.registryAccess());
        if (blade.isEmpty() || !player.addItem(blade)) {
            player.drop(blade, false);
        }
        player.displayClientMessage(Component.translatable("commands.mistletoe.give", player.getDisplayName()), false);
        return 1;
    }

    private static int audit(net.minecraft.commands.CommandSourceStack source, float targetMax, float playerCurrent, float playerMax) {
        float normal = MistletoeFormulas.normalDamage(
                TerminalBladeConfig.baseDamage(),
                TerminalBladeConfig.gapCoefficient(),
                targetMax,
                playerCurrent,
                TerminalBladeConfig.clampNegativeGap());
        boolean enhanced = MistletoeFormulas.isEnhancedSlashArt(targetMax, TerminalBladeConfig.slashArtThreshold());
        float cost = enhanced ? MistletoeFormulas.sacrificeCost(playerCurrent, TerminalBladeConfig.slashArtSacrificeRatio()) : 0.0F;
        float after = Math.max(0.0F, playerCurrent - cost);
        float sa = MistletoeFormulas.slashArtDamage(
                TerminalBladeConfig.baseDamage(),
                TerminalBladeConfig.gapCoefficient(),
                targetMax,
                enhanced ? after : playerCurrent,
                TerminalBladeConfig.clampNegativeGap(),
                TerminalBladeConfig.slashArtThreshold(),
                TerminalBladeConfig.slashArtMaxHealthBonus(),
                1.0F);
        MistletoeFormulas.SuperSacrifice superSacrifice = MistletoeFormulas.superSacrifice(
                playerCurrent, playerMax, TerminalBladeConfig.superSacrificeRatio());
        source.sendSuccess(() -> Component.translatable("commands.mistletoe.audit",
                normal, enhanced, cost, sa, superSacrifice.cost(), superSacrifice.lethal(),
                TerminalBladeConfig.superRadius()), false);
        return 1;
    }
}
