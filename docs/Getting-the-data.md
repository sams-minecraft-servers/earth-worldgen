# Getting the data

The mod reads three kinds of data from disk. None of it is included in this repository; the
scripts in `tools/` fetch it from public sources and pack it into the form the mod reads. Every
script can be stopped and started again: finished files are skipped.

The scripts keep everything under one data folder: `$EARTH_DATA` if set, otherwise
`~/earth-data`.

| Data | Source | Needed? | Packed size (whole planet) |
|---|---|---|---|
| Elevation | Terrain Tiles on AWS ("skadi" one-degree tiles, including sea depths) | **Yes** | about 132 GB (190 GB while downloading) |
| Land cover | ESA WorldCover 2021, 10 m resolution | Strongly recommended: biomes, trees, towns | about 116 GB |
| Streets and buildings | Overture Maps (OpenStreetMap plus traced buildings) | Optional | tens of GB, depending on how many cities |

## 1. Download elevation and land cover

```
tools/download_data.py list            # what there is to fetch, and how big it is
tools/download_data.py fetch 20M       # fetch everything missing, at most 20 MB/s
```

The rate limit matters on a shared machine: a fast download can saturate the disk and slow every
server on it. Keep it low (4 MB/s or less) if live servers share the disk.

**Just one region?** The mod treats any missing elevation tile as open sea, so you can fetch only
the tiles you need. Elevation tiles are one degree square and named by their south-west corner,
e.g. `N45E006` covers 45–46° N, 6–7° E:

```
mkdir -p ~/earth-data/hgt-gz/N45
curl -o ~/earth-data/hgt-gz/N45/N45E006.hgt.gz \
     https://s3.amazonaws.com/elevation-tiles-prod/skadi/N45/N45E006.hgt.gz
```

Land-cover files are 3 × 3 degree GeoTIFFs from the `esa-worldcover` bucket (`v200/2021/map/`).
Save them as published, e.g. `ESA_WorldCover_10m_2021_v200_N45E006_Map.tif`, in `~/earth-data/worldcover/`.

## 2. Pack the elevation

The downloaded tiles are gzip files that would have to be unpacked whole to read one height.
`PackElevation` repacks them, exactly, into small separately compressed squares so the mod only
unpacks what it needs:

```
java -Xmx1g tools/PackElevation.java ~/earth-data/hgt-gz ~/earth-data/elevation 4
```

The last number is how many threads to use. Each packed file is read back and checked against the
download before it gets its final name. Once packing is done, the `hgt-gz` folder can be deleted.

Land cover needs no packing: the mod reads the GeoTIFFs as published.

## 3. Optional: streets and buildings

`tools/pack_urban.py` packs building outlines (with heights and floors where known), roads,
railways and bridges for one-degree tiles, reading only the parts it needs straight from Overture's
public bucket:

```
python3 -m venv ~/earth-data/urban-venv
~/earth-data/urban-venv/bin/pip install duckdb
~/earth-data/urban-venv/bin/python tools/pack_urban.py N48E002 N51W001     # Paris, London
```

`tools/pack_planet.py` packs every tile that has a town in it, most populous first; it takes days.
Where no streets file exists, chunks get terrain without buildings. To turn streets or buildings
off without deleting the files, use the `cities` settings in [Configuration](Configuration.md).

Streets and buildings are only built into chunks made **after** their tile was packed. Chunks that
already exist are never changed.

## Licences

Credit the sources wherever players can see it. The exact wording is in
[ATTRIBUTION.md](../ATTRIBUTION.md).

## Keeping the machine safe

Packing is light, but the download scripts and the test server can use a lot of disk bandwidth
and memory. `tools/guard.sh` runs a command under a hard memory limit and refuses to start it when
the machine is short of memory:

```
tools/guard.sh 2G ~/earth-data/urban-venv/bin/python tools/pack_urban.py N48E002
```

It uses `systemd-run --user`, so it works on Linux with systemd only.
