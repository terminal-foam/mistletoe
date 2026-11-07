package dev.xingguang.terminalblade.registry;

import dev.xingguang.terminalblade.TerminalBlade;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, TerminalBlade.MOD_ID);

    public static final RegistryObject<SoundEvent> DRAW = register("draw");
    public static final RegistryObject<SoundEvent> SWING = register("swing");
    public static final RegistryObject<SoundEvent> HIT = register("hit");
    public static final RegistryObject<SoundEvent> SACRIFICE = register("sacrifice");
    public static final RegistryObject<SoundEvent> SUPER_CHARGE = register("super_charge");
    public static final RegistryObject<SoundEvent> SUPER_RELEASE = register("super_release");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(TerminalBlade.id(name)));
    }
}
