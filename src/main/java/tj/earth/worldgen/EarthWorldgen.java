package tj.earth.worldgen;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EarthWorldgen implements ModInitializer {
    public static final String ID = "earth_worldgen";
    public static final Logger LOG = LoggerFactory.getLogger("EarthWorldgen");

    private static boolean registered;

    /** Called once from BuiltInRegistriesMixin, while the game's registries can still be added to. */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath(ID, "surface"), ElevationFunction.CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath(ID, "below_ground"), BelowGround.CODEC);
        Registry.register(BuiltInRegistries.MATERIAL_RULE_TYPE, Identifier.fromNamespaceAndPath(ID, "ground"), GroundRule.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, Identifier.fromNamespaceAndPath(ID, "earth"), EarthBiomeSource.CODEC);
    }

    @Override
    public void onInitialize() {
        if (!registered) throw new IllegalStateException("Earth worldgen was not registered: BuiltInRegistriesMixin did not run");
    }
}
