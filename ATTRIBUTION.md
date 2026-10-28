# Data sources and attribution

The mod ships with no map data. The scripts in `tools/` download it from the public sources below.
If you run a world made with this mod where other people can see it (a public server, videos,
screenshots, a map website), show the credits below somewhere they can read them, such as your
server's website or its `/about` page.

## Elevation: Terrain Tiles

One-degree "skadi" tiles from the Terrain Tiles dataset on the Registry of Open Data on AWS
(`s3://elevation-tiles-prod`), originally produced by Mapzen. They combine many public elevation
sources, including SRTM, GMTED2010 and ETOPO1, and several national datasets. Some of those sources
require their own notice; the full list and wording are here:
https://github.com/tilezen/joerd/blob/master/docs/attribution.md

Short form:

> Elevation: Terrain Tiles (Mapzen, Registry of Open Data on AWS), from SRTM, GMTED2010, ETOPO1
> and other sources listed at github.com/tilezen/joerd.

## Land cover: ESA WorldCover 2021

ESA WorldCover 10 m 2021 v200, licensed under Creative Commons Attribution 4.0 International
(CC BY 4.0). Required credit:

> © ESA WorldCover project 2021 / Contains modified Copernicus Sentinel data (2021) processed by
> ESA WorldCover consortium.

https://esa-worldcover.org

## Streets and buildings: Overture Maps (optional)

Building outlines, roads and railways from Overture Maps, which draws on OpenStreetMap and other
sources. The parts derived from OpenStreetMap are licensed under the Open Database License (ODbL).
Required credit:

> © OpenStreetMap contributors, Overture Maps Foundation

https://docs.overturemaps.org/attribution/

## Country borders: Natural Earth

The country borders shipped with the mod (`countries.bin.gz`, used for the per-country settings)
are Natural Earth's 1:10m Admin 0 countries, which are in the public domain. No credit is required,
but it is welcome: "Made with Natural Earth." https://www.naturalearthdata.com

## Minecraft

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
