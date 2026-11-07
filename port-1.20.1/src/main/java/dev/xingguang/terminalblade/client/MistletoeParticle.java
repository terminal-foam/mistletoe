package dev.xingguang.terminalblade.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public class MistletoeParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    public MistletoeParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, float size) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        this.lifetime = 14 + this.random.nextInt(10);
        this.gravity = 0.012F;
        this.quadSize = size;
        this.xd = dx;
        this.yd = dy + 0.02D;
        this.zd = dz;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.removed) {
            this.setSpriteFromAge(this.sprites);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class PetalProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public PetalProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new MistletoeParticle(level, x, y, z, dx, dy, dz, this.sprites, 0.16F);
        }
    }

    public static class BerryProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public BerryProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            MistletoeParticle particle = new MistletoeParticle(level, x, y, z, dx * 0.4D, dy * 0.2D, dz * 0.4D, this.sprites, 0.08F);
            particle.gravity = 0.04F;
            return particle;
        }
    }

    public static class SparkProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public SparkProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            MistletoeParticle particle = new MistletoeParticle(level, x, y, z, dx, Math.abs(dy) + 0.03D, dz, this.sprites, 0.10F);
            particle.gravity = -0.005F;
            return particle;
        }
    }
}
