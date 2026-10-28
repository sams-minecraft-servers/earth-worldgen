# Radio

Country radio: one station for each country, heard clearly inside it and, through growing static,
for a while beyond its border. Players find a station by setting foot in its country; it then stays
on their dial for good.

The mod comes with **no stations**. You supply the audio (only use audio you have the right to play
to your players), build the packs with `tools/make_radio_packs.py`, and put them on a web server.
Until then the radio does nothing.

## For players

```
/radio tune <country>     listen to that country's station (its name or two-letter code: /radio tune fr)
/radio off                stop listening
/radio dial               the stations on your dial
```

The first time you tune to a station on each visit, your game downloads its sounds and shows its
loading screen for a moment. Server resource packs must be allowed for the server (Multiplayer,
Edit, Server Resource Packs: Enabled or Prompt). The radio plays on the **Jukebox/Note Blocks**
volume slider.

## Setting it up

1. **Record or collect the audio** as Ogg Vorbis files (`.ogg`), in one folder per country named by
   its two-letter code. A `station.txt` with the station's name is optional. An optional `static`
   folder holds a few short crackles for the fade near borders.

   ```
   audio/
     fr/  station.txt  01.ogg  02.ogg ...
     de/  station.txt  01.ogg ...
     static/  1.ogg  2.ogg
   ```

   Several five-minute parts per station work well: they play in turn, round and round, starting at
   a random one. Each part is streamed, so long parts are fine.

2. **Build the packs:**

   ```
   tools/make_radio_packs.py audio radio-packs
   ```

   This writes `fr.zip`, `de.zip`, …, `static.zip` and `manifest.json` into `radio-packs/`.

3. **Put `radio-packs/` on a web server** that players' games can reach, e.g.
   `https://example.com/radio/`.

4. **Point the mod at it** in `config/earth-mod/radio.json`:

   ```json
   "manifest": "https://example.com/radio/manifest.json",
   "packBaseUrl": "https://example.com/radio/"
   ```

   The manifest can also be a file path on the server. Then `/radio reload`, or restart.

To change a station, rebuild and upload. With `autoUpdate` on, the server reads the manifest again
every `updateEveryMinutes`, and players get the new version the next time they tune in.

## Settings (`config/earth-mod/radio.json`)

The file is written with every setting and its default the first time the server starts.

| Setting | Default | Meaning |
|---|---|---|
| `enabled` | `true` | The whole radio. `false`: no `/radio` command, no dial, no packs sent. Needs a restart. |
| `manifest` | `""` | The `manifest.json`, as a file path or an `http(s)://` link. Empty: no stations. |
| `packBaseUrl` | `""` | Where players' games download the packs; each pack's file name is added to it. |
| `autoUpdate` | `true` | Read the manifest again on a timer, so new recordings and stations arrive without a restart. |
| `updateEveryMinutes` | `60` | How often (at least 5). |
| `disabledCountries` | `[]` | Two-letter codes of countries whose stations are never on the air. |
| `discoverByVisiting` | `true` | A station joins a player's dial when they first set foot in its country. `false`: every station is on everyone's dial. |
| `announceNewStations` | `true` | Tell players when a station joins their dial. |
| `reachKm` | `400` | How far beyond its border a station fades out, in km. `0`: only inside the country. |
| `staticNoise` | `true` | Crackle, louder as the signal weakens. |
| `loadSeconds` | `120` | How long a player's game may take to download and load a station. |
| `prompt` | `""` | Text on the player's "download this pack?" screen; empty for the game's wording. |
| `testers` | `[]` | Player names who may hear stations marked `"permission": "no"` in the manifest. |

Operators can use `/radio reload` to read `radio.json` and the manifest again.

## The manifest

`make_radio_packs.py` writes it, but you can also write your own:

```json
{
  "packs": {
    "fr": {
      "file": "fr.zip",
      "sha1": "40 hex digits of fr.zip's SHA-1",
      "station": "Radio Example",
      "country": "France",
      "parts": [
        { "sound": "earthradio:fr.00", "seconds": 300 },
        { "sound": "earthradio:fr.01", "seconds": 300 }
      ]
    }
  },
  "static": { "file": "static.zip", "sha1": "...", "sounds": ["static.0", "static.1"], "seconds": 3 }
}
```

A station may give a full `"url"` instead of `"file"`. A station with `"permission"` set to anything
but `"yes"` is kept off the air, except for `testers`; leave the field out for your own audio.
Players' dials are kept in the world folder, in `earth_radio_dial.json`.

## Status

The commands, settings, manifest reading, country detection and border distances have been
tested on a test server. Playing sound to a real game client has not been tested yet; please
[open an issue](https://github.com/sams-minecraft-servers/earth-worldgen/issues) if it misbehaves.
