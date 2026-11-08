package dev.xingguang.terminalblade.combat;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.blade.MistletoeBlade;
import dev.xingguang.terminalblade.registry.ModSounds;
import mods.flammpfeil.slashblade.capability.inputstate.IInputState;
import mods.flammpfeil.slashblade.event.InputCommandEvent;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = TerminalBlade.MOD_ID)
public final class MistletoeHooks {
    private MistletoeHooks() {
    }

    /**
     * 旧暮色配方现在产出朝露；这里补刀铭，让合成出来的朝露也走
     * “封 → 附魔 → 妖”的路线，和 /mistletoe give dawn 一致。
     */
    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack crafted = event.getCrafting();
        if (MistletoeBlade.isDawn(crafted) && !crafted.hasCustomHoverName()) {
            crafted.setHoverName(MistletoeBlade.dawnName());
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
        var input = player.getCapability(mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState.INPUT_STATE).resolve().orElse(null);
        return input != null
                && input.getCommands().contains(InputCommand.SPRINT)
                && (!InputCommand.anyMatch(input.getCommands(), InputCommand.move) || !input.getCommands().contains(InputCommand.SNEAK))
                && input.getLastPressTime(InputCommand.SPRINT) == pressTime;
    }

    private static void play(LivingEntity entity, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}
