package dev.xingguang.terminalblade.combat;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.blade.MistletoeBlade;
import dev.xingguang.terminalblade.registry.ModSounds;
import mods.flammpfeil.slashblade.capability.inputstate.IInputState;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = TerminalBlade.MOD_ID)
public final class MistletoeHooks {
    private MistletoeHooks() {
    }

    @SubscribeEvent
    public static void onCombo(SlashBladeEvent.NextComboEvent event) {
        if (event.getUser().level().isClientSide() || !MistletoeBlade.isMistletoe(event.getBlade())) {
            return;
        }
        var state = event.getSlashBladeState();
        var current = state.getComboSeq();
        if (ComboStateRegistry.STANDBY.getId().equals(current) || ComboStateRegistry.NONE.getId().equals(current)) {
            play(event.getUser(), ModSounds.DRAW.get(), 0.65F, 1.15F);
        }
    }

    @SubscribeEvent
    public static void onInput(InputCommandEvent event) {
        ServerPlayer player = event.getEntity();
        if (!MistletoeBlade.isMistletoe(player)) {
            return;
        }
        if (MistletoeBlade.isDawn(player.getMainHandItem())) {
            return;
        }
        boolean pressed = !event.getOld().contains(InputCommand.SPRINT) && event.getCurrent().contains(InputCommand.SPRINT);
        if (!pressed) {
            return;
        }
        long pressTime = event.getState().getLastPressTime(InputCommand.SPRINT);
        IInputState input = event.getState();
        input.getScheduler().schedule("mistletoeCharge", pressTime + 5L, (entity, queue, now) -> {
            if (!(entity instanceof ServerPlayer serverPlayer) || !stillHolding(serverPlayer, pressTime)) {
                return;
            }
            play(serverPlayer, ModSounds.SUPER_CHARGE.get(), 0.7F, 1.4F);
            if (serverPlayer.level() instanceof ServerLevel level) {
                MistletoeDamage.burst(level, serverPlayer.getX(), serverPlayer.getY(0.6D), serverPlayer.getZ(), 8, 0.25D);
            }
        });
    }

    private static boolean stillHolding(ServerPlayer player, long pressTime) {
        if (!MistletoeBlade.isMistletoe(player) || MistletoeBlade.isBrokenOrSealed(player.getMainHandItem())) {
            return false;
        }
        var input = player.getData(mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState.INPUT_STATE.get());
        return input != null
                && input.getCommands().contains(InputCommand.SPRINT)
                && (!InputCommand.anyMatch(input.getCommands(), InputCommand.move) || !input.getCommands().contains(InputCommand.SNEAK))
                && input.getLastPressTime(InputCommand.SPRINT) == pressTime;
    }

    private static void play(LivingEntity entity, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}
