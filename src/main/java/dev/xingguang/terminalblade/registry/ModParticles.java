package dev.xingguang.terminalblade.registry;

import dev.xingguang.terminalblade.TerminalBlade;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, TerminalBlade.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PETAL = register("petal");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BERRY = register("berry");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPARK = register("spark");

    private ModParticles() {
    }

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> register(String name) {
        return PARTICLES.register(name, () -> new SimpleParticleType(false));
    }
}
