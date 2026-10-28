package tj.earth.worldgen;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
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
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) installDatapack();
    }

    /**
     * Puts the mod's datapack (shipped inside the jar) into a world that has not been made yet, so the
     * mod works by dropping the jar into mods/: the datapack must be there before the world's first
     * start. A world that already exists is left alone.
     */
    private static void installDatapack() {
        try {
            if (!EarthConfig.get().installDatapack) return;
            String level = "world";
            Path properties = Path.of("server.properties");
            if (Files.exists(properties)) {
                Properties p = new Properties();
                try (Reader in = Files.newBufferedReader(properties)) { p.load(in); }
                level = p.getProperty("level-name", "world").trim();
            }
            Path world = Path.of(level);
            if (Files.exists(world.resolve("level.dat"))) return;
            Path pack = world.resolve("datapacks").resolve(ID);
            if (Files.exists(pack)) return;
            try (InputStream raw = EarthWorldgen.class.getResourceAsStream("/earth_worldgen/datapack.zip")) {
                if (raw == null) throw new IOException("datapack.zip is not in the mod");
                ZipInputStream zip = new ZipInputStream(raw);
                for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                    Path to = pack.resolve(entry.getName()).normalize();
                    if (!to.startsWith(pack)) continue;
                    if (entry.isDirectory()) { Files.createDirectories(to); continue; }
                    Files.createDirectories(to.getParent());
                    Files.copy(zip, to);
                }
            }
            LOG.info("Earth: new world \"{}\": installed the mod's datapack in {}", level, pack);
        } catch (Exception e) {
            LOG.error("Earth: could not install the datapack into the new world ({}); copy datapack/ into <world>/datapacks/ by hand", e.getMessage());
        }
    }
}
