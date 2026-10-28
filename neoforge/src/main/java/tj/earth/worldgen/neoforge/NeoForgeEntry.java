package tj.earth.worldgen.neoforge;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.RegisterEvent;
import tj.earth.worldgen.EarthWorldgen;

/** NeoForge's way in: the mod's entries are added through RegisterEvent. */
@Mod(EarthWorldgen.ID)
public final class NeoForgeEntry {
    public NeoForgeEntry(final IEventBus modBus, final Dist dist) {
        modBus.addListener(RegisterEvent.class, NeoForgeEntry::register);
        EarthWorldgen.start(dist == Dist.DEDICATED_SERVER);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void register(final RegisterEvent event) {
        EarthWorldgen.eachEntry((registry, id, value) -> {
            if (event.getRegistryKey().equals(registry)) event.register((ResourceKey) registry, id, (Supplier) () -> value);
        });
    }
}
