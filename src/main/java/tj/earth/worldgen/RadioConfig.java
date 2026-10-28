package tj.earth.worldgen;

import java.util.List;

/**
 * The settings for the country radio, in config/earth-mod/radio.json (see ConfigFile and Radio).
 * Read at start-up; "/radio reload" reads it again (except "enabled", which needs a restart).
 */
public final class RadioConfig {
    /** The whole radio: false means no /radio command, no dial and no sound packs at all. Needs a restart to change. */
    public boolean enabled = true;
    /**
     * Where the list of stations is: a manifest.json (see docs/Radio.md and tools/make_radio_packs.py),
     * as a file path or an http(s) link. Empty: the radio has no stations.
     */
    public String manifest = "";
    /** Where players' games download the packs from, e.g. "https://example.com/radio/"; a pack's "file" is added to it. */
    public String packBaseUrl = "";
    /** Read the manifest again every so often, so new recordings and stations reach players without a restart. */
    public boolean autoUpdate = true;
    /** How often, in minutes. */
    public int updateEveryMinutes = 60;
    /** Two-letter country codes whose stations are never on the air. */
    public List<String> disabledCountries = List.of();
    /** true: a country's station joins a player's dial the first time they set foot in that country. false: every station is on everyone's dial. */
    public boolean discoverByVisiting = true;
    /** Tell players when a new station joins their dial. */
    public boolean announceNewStations = true;
    /** How far beyond its border a station can still be heard, fading into static, in km. 0: only inside the country. */
    public int reachKm = 400;
    /** Crackle louder as the signal weakens near the edge of a station's reach. */
    public boolean staticNoise = true;
    /** How long a player's game may take to download and load a station, in seconds. */
    public int loadSeconds = 120;
    /** Text shown on the player's "download this pack?" screen; empty for the game's own wording. */
    public String prompt = "";
    /** Players (names) who may hear stations listed with "permission": "no" in the manifest, to test them. */
    public List<String> testers = List.of();

    private static volatile RadioConfig instance;

    public static RadioConfig get() {
        RadioConfig c = instance;
        return c != null ? c : reload();
    }

    public static synchronized RadioConfig reload() {
        instance = ConfigFile.read("radio.json", RadioConfig.class, new RadioConfig(), null);
        return instance;
    }
}
