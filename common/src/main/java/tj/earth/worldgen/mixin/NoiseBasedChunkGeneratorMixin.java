package tj.earth.worldgen.mixin;

import net.minecraft.world.level.chunk.ChunkGenerator;
import tj.earth.worldgen.Earth;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tj.earth.worldgen.CityVillagers;
import tj.earth.worldgen.EarthCarvingMask;
import tj.earth.worldgen.EarthFill;
import tj.earth.worldgen.EarthFluids;
import tj.earth.worldgen.Fauna;
import tj.earth.worldgen.Monuments;

/**
 * Gives the Earth dimension its own rule for where water stands (see EarthFluids), its own, much
 * shorter, way of turning the noise settings into blocks (see EarthFill), and caves that leave the
 * ground under water alone (see EarthCarvingMask), villagers in its towns (see CityVillagers) and axolotls in Mexico City's lakes (see Fauna).
 * Other dimensions are untouched.
 */
@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseBasedChunkGeneratorMixin {
    @Shadow @Final
    private Holder<NoiseGeneratorSettings> settings;

    @Inject(method = "doFill", at = @At("HEAD"), cancellable = true)
    private void earth$fill(final NoiseChunk noiseChunk, final ChunkAccess chunk, final CallbackInfo callback) {
        NoiseGeneratorSettings settings = this.settings.value();
        if (Earth.is((ChunkGenerator) (Object) this)) {
            EarthFill.fill(settings, noiseChunk, chunk);
            callback.cancel();
        }
    }

    @Inject(method = "createFluidPicker", at = @At("HEAD"), cancellable = true)
    private static void earth$fluids(final NoiseGeneratorSettings settings, final CallbackInfoReturnable<Aquifer.FluidPicker> callback) {
        if (EarthFluids.isEarth(settings)) callback.setReturnValue(EarthFluids.picker(settings));
    }

    @Inject(method = "spawnOriginalMobs", at = @At("TAIL"))
    private void earth$villagers(final WorldGenRegion region, final CallbackInfo callback) {
        if (Earth.is((ChunkGenerator) (Object) this)) {
            CityVillagers.settle(region);
            Fauna.settleAxolotls(region);
            Monuments.settle(region);
        }
    }

    @Redirect(method = "generateCarvers", at = @At(value = "NEW", target = "net/minecraft/world/level/chunk/CarvingMask"))
    private CarvingMask earth$carvingMask(final int minY, final int maxY, final ChunkAccess chunk, final Blender blender, final NoiseChunk noiseChunk,
                                          final RandomState randomState, final BiomeManager biomeManager, final WorldGenRegion carverBiomeRegion, final MaterialRule materialRule) {
        return Earth.is((ChunkGenerator) (Object) this) ? new EarthCarvingMask(minY, maxY, chunk.getPos()) : new CarvingMask(minY, maxY);
    }
}
