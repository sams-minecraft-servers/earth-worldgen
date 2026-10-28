package tj.earth.worldgen.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import tj.earth.worldgen.EarthWorldgen;

/** Fabric's way in. The mod's entries are added by BuiltInRegistriesMixin, before the registries close. */
public final class FabricEntry implements ModInitializer {
    @Override
    public void onInitialize() {
        EarthWorldgen.start(FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER);
    }
}
