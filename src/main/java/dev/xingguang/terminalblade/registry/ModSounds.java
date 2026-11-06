package dev.xingguang.terminalblade.registry;

import dev.xingguang.terminalblade.TerminalBlade;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, TerminalBlade.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> DRAW = register("draw");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWING = register("swing");
    public static final DeferredHolder<SoundEvent, SoundEvent> HIT = register("hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> SACRIFICE = register("sacrifice");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPER_CHARGE = register("super_charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPER_RELEASE = register("super_release");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(TerminalBlade.id(name)));
    }
}
