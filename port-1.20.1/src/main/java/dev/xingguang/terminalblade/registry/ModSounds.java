package dev.xingguang.terminalblade.registry;

import dev.xingguang.terminalblade.TerminalBlade;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, TerminalBlade.MOD_ID);

    public static final RegistryObject<SoundEvent> SACRIFICE = register("sacrifice");
    public static final RegistryObject<SoundEvent> SUPER_CHARGE = register("super_charge");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(TerminalBlade.id(name)));
    }
}
