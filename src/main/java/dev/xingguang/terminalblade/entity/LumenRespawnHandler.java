package dev.xingguang.terminalblade.entity;

import dev.xingguang.terminalblade.TerminalBlade;
import dev.xingguang.terminalblade.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 已臣服的随从死亡后，像玩家一样在主人的重生点重生。
 *
 * <p>延迟 {@link #RESPAWN_DELAY_TICKS} tick 后，取主人当前的
 * {@code getRespawnPosition()} / {@code getRespawnDimension()}；主人没设重生点就落在主人脚下。
 * 主人不在线时保持等待。</p>
 */
@EventBusSubscriber(modid = TerminalBlade.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class LumenRespawnHandler {
    private static final int RESPAWN_DELAY_TICKS = 200;

    private static final List<Pending> PENDING = new ArrayList<>();

    private LumenRespawnHandler() {
    }

    private record Pending(UUID owner, long dueTick, String name) {
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof LumenCompanionEntity pet) || pet.level().isClientSide()) {
            return;
        }
        if (!pet.isTame()) {
            return;
        }
        UUID owner = pet.getOwnerUUID();
        if (owner == null) {
            return;
        }
        PENDING.add(new Pending(owner,
                pet.level().getGameTime() + RESPAWN_DELAY_TICKS,
                pet.getName().getString()));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        PENDING.removeIf(pending -> tryRespawn(server, pending));
    }

    private static boolean tryRespawn(MinecraftServer server, Pending pending) {
        if (server.overworld().getGameTime() < pending.dueTick()) {
            return false;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(pending.owner());
        if (owner == null) {
            // 主人不在线：等他回来再重生
            return false;
        }

        ResourceKey<Level> dimension = owner.getRespawnDimension();
        BlockPos respawnPos = owner.getRespawnPosition();
        ServerLevel target = dimension == null ? null : server.getLevel(dimension);
        if (target == null || respawnPos == null) {
            target = owner.serverLevel();
            respawnPos = owner.blockPosition();
        }

        LumenCompanionEntity entity = ModEntities.LUMEN_COMPANION.get().create(target);
        if (entity == null) {
            return true;
        }
        entity.moveTo(respawnPos.getX() + 0.5D, respawnPos.getY() + 0.1D, respawnPos.getZ() + 0.5D,
                owner.getYRot(), 0.0F);
        entity.setTame(true, true);
        entity.setOwnerUUID(pending.owner());
        entity.setCustomName(Component.literal(pending.name()));
        var maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(LumenCompanionEntity.MAX_HEALTH);
        }
        entity.setHealth(LumenCompanionEntity.MAX_HEALTH);
        target.addFreshEntity(entity);
        owner.sendSystemMessage(Component.translatable("entity.mistletoe.lumen_companion.respawned"));
        TerminalBlade.LOGGER.info("[流明·星] 随从在主人重生点复活：{}", respawnPos);
        return true;
    }
}
