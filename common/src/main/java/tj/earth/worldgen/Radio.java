package tj.earth.worldgen;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.io.InputStream;
import java.io.Reader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Country radio: one station for each country, heard clearly inside it and, through growing static,
 * for some way beyond its border (radio.json "reachKm"), then not at all.
 *
 *     /radio tune <country>    listen to that country's station (code or name)
 *     /radio off               stop
 *     /radio dial              the stations you can tune to
 *     /radio reload            read radio.json and the manifest again (operators)
 *
 * With "discoverByVisiting", a station joins a player's dial the first time they set foot in its
 * country, and stays for good (kept in the world folder, earth_radio_dial.json).
 *
 * Each station's sound is a resource pack of its own, listed in a manifest (see docs/Radio.md; made
 * by tools/make_radio_packs.py from audio the server owner supplies). A pack is sent to a player's
 * game the first time they tune to it on each visit; their game shows its loading screen for a
 * moment. The packs are never "required": a player who says no simply hears nothing.
 */
public final class Radio {
    private Radio() {}

    record Part(String sound, double seconds) {}

    record Station(String code, String country, String station, String file, String url, String sha1, String permission, List<Part> parts) {}

    record Static(String file, String url, String sha1, List<String> sounds, double seconds) {}

    record Manifest(Map<String, Station> stations, Static noise) {}

    /** A player listening, and where they are in the station's recordings. */
    private static final class Listener {
        final String code;
        int part;
        long nextPart, nextCrackle;
        boolean out;

        Listener(final String code, final int part) {
            this.code = code;
            this.part = part;
        }
    }

    /** Packs a player's game is loading, and what to do when all have loaded. */
    private record Waiting(Set<String> packs, String code, long until) {}

    private static final String STATIC = "er_static";
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build();
    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Earth radio");
        t.setDaemon(true);
        return t;
    });

    private static volatile Manifest manifest = new Manifest(Map.of(), null);
    private static ScheduledFuture<?> timer;
    private static MinecraftServer server;
    private static long tick;
    private static final Map<UUID, Listener> LISTENING = new HashMap<>();
    private static final Map<UUID, Map<String, String>> LOADED = new HashMap<>();   // packs each game holds this visit: name -> sha1
    private static final Map<UUID, Waiting> WAITING = new HashMap<>();
    private static final Map<UUID, String> COUNTRY = new HashMap<>();                // the country each player was last in
    private static final Map<UUID, Double> SIGNAL = new HashMap<>();
    private static final Map<UUID, String> NAMES = new ConcurrentHashMap<>();        // pack id -> pack name
    private static Map<String, Set<String>> dial = new HashMap<>();                  // player uuid -> station codes found
    private static boolean dialChanged;

    // ---------------------------------------------------------------- set-up

    public static boolean enabled() {
        return RadioConfig.get().enabled;
    }

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!enabled()) return;
        dispatcher.register(Commands.literal("radio")
            .then(Commands.literal("tune").then(Commands.argument("country", StringArgumentType.greedyString()).executes(Radio::tuneCommand)))
            .then(Commands.literal("off").executes(Radio::offCommand))
            .then(Commands.literal("dial").executes(Radio::dialCommand))
            .then(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(Radio::reloadCommand)));
    }

    /** Called once the server is running. */
    public static void started(final MinecraftServer s) {
        if (!enabled()) return;
        server = s;
        readDial();
        schedule();
    }

    private static synchronized void schedule() {
        if (timer != null) timer.cancel(false);
        RadioConfig c = RadioConfig.get();
        WORKER.execute(Radio::readManifest);
        if (c.autoUpdate && !c.manifest.isBlank()) {
            long every = Math.max(5, c.updateEveryMinutes);
            timer = WORKER.scheduleWithFixedDelay(Radio::readManifest, every, every, TimeUnit.MINUTES);
        }
    }

    private static void readManifest() {
        RadioConfig c = RadioConfig.get();
        if (c.manifest.isBlank()) { manifest = new Manifest(Map.of(), null); return; }
        try {
            String text;
            if (c.manifest.startsWith("http://") || c.manifest.startsWith("https://")) {
                HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(c.manifest)).timeout(Duration.ofMinutes(2)).build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw new IllegalStateException("the server answered " + response.statusCode());
                text = response.body();
            } else {
                text = Files.readString(Path.of(c.manifest), StandardCharsets.UTF_8);
            }
            JsonObject json = GSON.fromJson(text, JsonObject.class);
            Map<String, Station> stations = new LinkedHashMap<>();
            JsonObject packs = json.has("packs") ? json.getAsJsonObject("packs") : new JsonObject();
            for (String code : packs.keySet()) {
                JsonObject p = packs.getAsJsonObject(code);
                List<Part> parts = new ArrayList<>();
                p.getAsJsonArray("parts").forEach(e -> parts.add(new Part(e.getAsJsonObject().get("sound").getAsString(), e.getAsJsonObject().get("seconds").getAsDouble())));
                if (parts.isEmpty()) continue;
                String cc = code.toLowerCase();
                stations.put(cc, new Station(cc, text(p, "country", Countries.name(cc.toUpperCase())), text(p, "station", cc.toUpperCase() + " radio"),
                    text(p, "file", ""), text(p, "url", ""), text(p, "sha1", ""), text(p, "permission", "yes"), List.copyOf(parts)));
            }
            Static noise = null;
            if (json.has("static")) {
                JsonObject s = json.getAsJsonObject("static");
                List<String> sounds = new ArrayList<>();
                s.getAsJsonArray("sounds").forEach(e -> sounds.add(e.getAsString()));
                if (!sounds.isEmpty()) noise = new Static(text(s, "file", ""), text(s, "url", ""), text(s, "sha1", ""), List.copyOf(sounds), s.has("seconds") ? s.get("seconds").getAsDouble() : 3);
            }
            boolean first = manifest.stations.isEmpty();
            manifest = new Manifest(stations, noise);
            if (first) EarthWorldgen.LOG.info("Earth radio: {} stations", stations.size());
        } catch (Exception e) {
            EarthWorldgen.LOG.warn("Earth radio: the manifest {} could not be read ({}); keeping the stations already known", c.manifest, e.getMessage());
        }
    }

    private static String text(final JsonObject o, final String key, final String otherwise) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : otherwise;
    }

    // ---------------------------------------------------------------- every tick

    /** Called at the end of every server tick. */
    public static void tick(final MinecraftServer s) {
        if (server == null) return;
        tick++;
        if (tick % 40 == 0) {
            for (ServerPlayer player : s.getPlayerList().getPlayers()) where(player);
            if (dialChanged && tick % 1200 == 0) saveDial();
        }
        if (LISTENING.isEmpty() && WAITING.isEmpty()) return;
        for (Map.Entry<UUID, Waiting> e : List.copyOf(WAITING.entrySet())) {
            if (tick > e.getValue().until) {
                WAITING.remove(e.getKey());
                ServerPlayer p = s.getPlayerList().getPlayer(e.getKey());
                if (p != null) tell(p, "The station took too long to load. Try again in a moment.", ChatFormatting.RED);
            }
        }
        for (Map.Entry<UUID, Listener> e : List.copyOf(LISTENING.entrySet())) {
            ServerPlayer p = s.getPlayerList().getPlayer(e.getKey());
            if (p == null) { LISTENING.remove(e.getKey()); continue; }
            Listener l = e.getValue();
            if (tick >= l.nextPart) playNext(p, l);
            if (tick >= l.nextCrackle) crackle(p, l);
        }
    }

    /** Where a player is: a new country adds its station to their dial; the signal of what they listen to. */
    private static void where(final ServerPlayer player) {
        if (!Earth.is(player.level())) return;
        EarthConfig c = EarthConfig.get();
        double lat = c.latitude(player.getBlockZ()), lon = c.longitude(player.getBlockX());
        String here = Countries.at(lat, lon).toLowerCase();
        UUID id = player.getUUID();
        if (!here.equals(COUNTRY.put(id, here)) && !here.isEmpty()) found(player, here);
        Listener l = LISTENING.get(id);
        if (l != null) SIGNAL.put(id, signal(l.code, lat, lon));
    }

    private static double signal(final String code, final double lat, final double lon) {
        int reach = RadioConfig.get().reachKm;
        double d = Countries.distanceKm(lat, lon, code, Math.max(1, reach));
        if (d == 0) return 1;
        return reach <= 0 || Double.isInfinite(d) ? 0 : Math.max(0, 1 - d / reach);
    }

    private static void found(final ServerPlayer player, final String code) {
        RadioConfig c = RadioConfig.get();
        Station s = manifest.stations.get(code);
        if (s == null || !c.discoverByVisiting || !onAir(player, s)) return;
        if (dial.computeIfAbsent(player.getUUID().toString(), k -> new LinkedHashSet<>()).add(code)) {
            dialChanged = true;
            if (c.announceNewStations) tell(player, "New on your dial: " + s.station + ", from " + s.country + ". /radio tune " + code, ChatFormatting.GOLD);
        }
    }

    private static boolean onAir(final ServerPlayer player, final Station s) {
        RadioConfig c = RadioConfig.get();
        if (EarthConfig.listed(c.disabledCountries, s.code)) return false;
        return "yes".equalsIgnoreCase(s.permission) || c.testers.stream().anyMatch(t -> t.equalsIgnoreCase(player.getScoreboardName()));
    }

    private static boolean onDial(final ServerPlayer player, final Station s) {
        if (!onAir(player, s)) return false;
        if (!RadioConfig.get().discoverByVisiting) return true;
        Set<String> mine = dial.get(player.getUUID().toString());
        return mine != null && mine.contains(s.code);
    }

    // ---------------------------------------------------------------- playing

    private static void playNext(final ServerPlayer player, final Listener l) {
        Station s = manifest.stations.get(l.code);
        if (s == null || !onAir(player, s)) { stop(player, "The station has gone off the air."); return; }
        double strength = SIGNAL.getOrDefault(player.getUUID(), 1.0);
        if (strength <= 0) {
            if (!l.out) actionBar(player, s.station + ": out of range", ChatFormatting.GRAY);
            l.out = true;
            l.nextPart = tick + 20 * 20;   // listen again in a while: they may come back in range
            return;
        }
        boolean first = l.part < 0;
        if (first) l.part = (int) (Math.random() * s.parts.size());
        l.out = false;
        Part part = s.parts.get(l.part % s.parts.size());
        l.part++;
        sound(player, part.sound, (float) Math.max(0.15, strength));
        if (first || strength < 1) actionBar(player, s.station + ", " + s.country + (strength < 1 ? " (signal " + Math.round(strength * 100) + "%)" : ""), ChatFormatting.GOLD);
        l.nextPart = tick + Math.round(Math.max(5, part.seconds) * 20) + 30;
    }

    private static void crackle(final ServerPlayer player, final Listener l) {
        Static noise = manifest.noise;
        double strength = SIGNAL.getOrDefault(player.getUUID(), 1.0);
        if (noise != null && RadioConfig.get().staticNoise && !l.out && strength > 0 && strength < 1 && LOADED.getOrDefault(player.getUUID(), Map.of()).containsKey(STATIC)) {
            String sound = noise.sounds.get((int) (Math.random() * noise.sounds.size()));
            sound(player, sound.contains(":") ? sound : "earthradio:" + sound, (float) (0.15 + 0.75 * (1 - strength)));
        }
        l.nextCrackle = tick + Math.round((noise == null ? 3 : noise.seconds) * 20);
    }

    private static void sound(final ServerPlayer player, final String id, final float volume) {
        Holder<SoundEvent> event = Holder.direct(SoundEvent.createVariableRangeEvent(Identifier.parse(id)));
        player.connection.send(new ClientboundSoundPacket(event, SoundSource.RECORDS, player.getX(), player.getY(), player.getZ(), volume, 1f, 0L));
    }

    private static void stop(final ServerPlayer player, final String why) {
        if (LISTENING.remove(player.getUUID()) == null) return;
        player.connection.send(new ClientboundStopSoundPacket(null, SoundSource.RECORDS));
        if (why != null) tell(player, why, ChatFormatting.GRAY);
    }

    // ---------------------------------------------------------------- packs

    private static UUID packId(final String name) {
        UUID id = UUID.nameUUIDFromBytes(("earthradio:" + name).getBytes(StandardCharsets.UTF_8));
        NAMES.put(id, name);
        return id;
    }

    private static String linkTo(final String file, final String url) {
        if (!url.isBlank()) return url;
        String base = RadioConfig.get().packBaseUrl;
        return base.isBlank() || file.isBlank() ? null : (base.endsWith("/") ? base : base + "/") + file;
    }

    /** Sends a pack unless the player's game already holds this version; true if it was sent. */
    private static boolean send(final ServerPlayer player, final String name, final String sha1, final String link) {
        if (sha1.equalsIgnoreCase(LOADED.getOrDefault(player.getUUID(), Map.of()).get(name))) return false;
        String prompt = RadioConfig.get().prompt;
        player.connection.send(new ClientboundResourcePackPushPacket(packId(name), link, sha1.toLowerCase(), false,
            prompt.isBlank() ? Optional.empty() : Optional.of(Component.literal(prompt))));
        return true;
    }

    /** A player's game answering about one of the radio's packs; true when it was one of ours. Runs on the server thread. */
    public static boolean answered(final ServerPlayer player, final ServerboundResourcePackPacket answer) {
        String name = NAMES.get(answer.id());
        if (name == null || player == null) return false;
        UUID id = player.getUUID();
        Waiting w = WAITING.get(id);
        switch (answer.action()) {
            case ACCEPTED, DOWNLOADED -> { }
            case SUCCESSFULLY_LOADED -> {
                String sha1 = name.equals(STATIC) ? (manifest.noise == null ? "" : manifest.noise.sha1)
                    : Optional.ofNullable(manifest.stations.get(name.substring(3))).map(Station::sha1).orElse("");
                LOADED.computeIfAbsent(id, k -> new HashMap<>()).put(name, sha1);
                if (w != null && w.packs.remove(name) && w.packs.isEmpty()) {
                    WAITING.remove(id);
                    startListening(player, w.code);
                }
            }
            default -> {
                if (w != null && w.packs.contains(name)) {
                    WAITING.remove(id);
                    tell(player, answer.action() == ServerboundResourcePackPacket.Action.DECLINED
                        ? "Your game said no to the station's sounds. Server resource packs must be enabled for this server (Multiplayer, Edit, Server Resource Packs)."
                        : "The station could not be loaded. Try again in a moment.", ChatFormatting.RED);
                }
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- commands

    private static int tuneCommand(final CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        String text = StringArgumentType.getString(context, "country");
        String code = Countries.find(text);
        if (code == null) { tell(player, "No country called \"" + text + "\". Try its name or its two-letter code, e.g. /radio tune fr", ChatFormatting.RED); return 0; }
        code = code.toLowerCase();
        Station s = manifest.stations.get(code);
        String name = Countries.name(code.toUpperCase());
        if (s == null || !onAir(player, s)) { tell(player, name + " has no station on the air.", ChatFormatting.YELLOW); return 0; }
        if (!onDial(player, s)) { tell(player, "You have not found " + name + "'s station yet: it comes onto your dial the first time you set foot in " + name + ".", ChatFormatting.YELLOW); return 0; }
        String link = linkTo(s.file, s.url);
        if (link == null || s.sha1.isBlank()) { tell(player, "This station's sounds are not available right now.", ChatFormatting.RED); return 0; }
        stop(player, null);
        WAITING.remove(player.getUUID());
        Set<String> sent = new LinkedHashSet<>();
        Static noise = manifest.noise;
        if (noise != null && RadioConfig.get().staticNoise) {
            String staticLink = linkTo(noise.file, noise.url);
            if (staticLink != null && !noise.sha1.isBlank() && send(player, STATIC, noise.sha1, staticLink)) sent.add(STATIC);
        }
        if (send(player, "er_" + code, s.sha1, link)) sent.add("er_" + code);
        if (sent.isEmpty()) { startListening(player, code); return 1; }
        WAITING.put(player.getUUID(), new Waiting(sent, code, tick + RadioConfig.get().loadSeconds * 20L));
        tell(player, "Tuning to " + s.station + "... your game loads the station's sounds (a moment's loading screen).", ChatFormatting.GRAY);
        return 1;
    }

    private static void startListening(final ServerPlayer player, final String code) {
        Station s = manifest.stations.get(code);
        if (s == null) return;
        Listener l = new Listener(code, -1);
        l.nextPart = tick;
        l.nextCrackle = tick + 20;
        LISTENING.put(player.getUUID(), l);
        where(player);
        tell(player, "On air: " + s.station + ", " + s.country + ". /radio off to stop.", ChatFormatting.GREEN);
    }

    private static int offCommand(final CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        WAITING.remove(player.getUUID());
        if (LISTENING.containsKey(player.getUUID())) stop(player, "Radio off.");
        else tell(player, "The radio is already off.", ChatFormatting.GRAY);
        return 1;
    }

    private static int dialCommand(final CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        List<String> on = new ArrayList<>();
        for (Station s : manifest.stations.values()) if (onDial(player, s)) on.add(s.country + " (" + s.station + "): /radio tune " + s.code);
        if (on.isEmpty()) {
            tell(player, RadioConfig.get().discoverByVisiting ? "Your dial is empty. Each country you set foot in adds its station." : "No stations are on the air.", ChatFormatting.YELLOW);
            return 0;
        }
        tell(player, "On your dial: " + String.join("; ", on), ChatFormatting.AQUA);
        return on.size();
    }

    private static int reloadCommand(final CommandContext<CommandSourceStack> context) {
        RadioConfig.reload();
        schedule();
        context.getSource().sendSuccess(() -> Component.literal("Read radio.json and the station manifest again"), false);
        return 1;
    }

    // ---------------------------------------------------------------- players coming and going, and the dial on disk

    public static void left(final ServerPlayer player) {
        UUID id = player.getUUID();
        LISTENING.remove(id);
        LOADED.remove(id);
        WAITING.remove(id);
        COUNTRY.remove(id);
        SIGNAL.remove(id);
    }

    private static Path dialFile() {
        return server.getWorldPath(LevelResource.ROOT).resolve("earth_radio_dial.json");
    }

    private static void readDial() {
        try {
            Path file = dialFile();
            if (!Files.exists(file)) return;
            try (Reader in = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
                Map<String, Set<String>> read = GSON.fromJson(in, new TypeToken<Map<String, LinkedHashSet<String>>>() {}.getType());
                if (read != null) dial = new HashMap<>(read);
            }
        } catch (Exception e) {
            EarthWorldgen.LOG.warn("Earth radio: the dial could not be read ({}); starting with empty dials", e.getMessage());
        }
    }

    /** Writes the dial to the world folder. Called now and then, and when the server stops. */
    public static void saveDial() {
        if (server == null || !dialChanged) return;
        try {
            Path file = dialFile(), part = file.resolveSibling("earth_radio_dial.json.part");
            Files.writeString(part, GSON.toJson(dial), StandardCharsets.UTF_8);
            Files.move(part, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            dialChanged = false;
        } catch (Exception e) {
            EarthWorldgen.LOG.warn("Earth radio: the dial could not be saved: {}", e.getMessage());
        }
    }

    private static void tell(final ServerPlayer player, final String text, final ChatFormatting colour) {
        player.sendSystemMessage(Component.literal(text).withStyle(colour));
    }

    private static void actionBar(final ServerPlayer player, final String text, final ChatFormatting colour) {
        player.sendSystemMessage(Component.literal(text).withStyle(colour), true);
    }
}
