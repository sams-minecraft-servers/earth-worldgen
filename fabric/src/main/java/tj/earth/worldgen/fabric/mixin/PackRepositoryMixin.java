package tj.earth.worldgen.fabric.mixin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.flag.FeatureFlagSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tj.earth.worldgen.EarthWorldgen;

/**
 * Fabric on its own does not load the data and assets inside mods (Fabric API does, and NeoForge
 * always does). Without Fabric API, this adds the mod's jar as a built-in pack, always on, to the
 * game's data packs (for the Earth world type) and, on a client, its resource packs (for the name
 * "Earth" on the Create World screen).
 */
@Mixin(PackRepository.class)
abstract class PackRepositoryMixin {
    @Shadow @Final @Mutable private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void earth$addModPack(final RepositorySource[] given, final CallbackInfo callback) {
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.isModLoaded("fabric-resource-loader-v0") || loader.isModLoaded("fabric-resource-loader-v1")) return;
        boolean data = sources.stream().anyMatch(source -> source instanceof ServerPacksSource);
        boolean assets = sources.stream().anyMatch(source -> source.getClass().getName().equals("net.minecraft.client.resources.ClientPackSource"));
        if (!data && !assets) return;
        Optional<Path> root = loader.getModContainer(EarthWorldgen.ID).flatMap(mod -> mod.getRootPaths().stream()
            .filter(path -> Files.isDirectory(path.resolve(data ? "data" : "assets").resolve(EarthWorldgen.ID))).findFirst());
        if (root.isEmpty()) return;
        Set<RepositorySource> more = new LinkedHashSet<>(sources);
        more.add(consumer -> consumer.accept(new Pack(
            new PackLocationInfo(EarthWorldgen.ID, Component.literal("Earth Worldgen"), PackSource.BUILT_IN, Optional.empty()),
            new PathPackResources.PathResourcesSupplier(root.get()),
            new Pack.Metadata(Component.literal("The Earth world type"), PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of()),
            new PackSelectionConfig(true, Pack.Position.TOP, false))));
        sources = java.util.Collections.unmodifiableSet(more);
    }
}
