# Finding places

The world is a plain latitude/longitude grid (an equirectangular map):

```
blocks per degree = 40,075,016.686 / horizontalScale / 360     (≈ 22,263.9 at 1:5)
X = longitude × blocks per degree        (east is +X)
Z = −latitude × blocks per degree        (north is −Z, as in the game)
```

And back again:

```
longitude = X / blocks per degree
latitude  = −Z / blocks per degree
```

## Examples at 1:5

| Place | Latitude, longitude | X | Z |
|---|---|---|---|
| Eiffel Tower, Paris | 48.8584, 2.2945 | 51,085 | −1,087,778 |
| Empire State Building, New York | 40.7484, −73.9857 | −1,647,210 | −907,218 |
| Mount Everest | 27.9881, 86.9250 | 1,935,289 | −623,124 |
| Nairobi | −1.2864, 36.8172 | 819,694 | 28,640 |

To go somewhere as an operator, use a high Y so you don't land inside a mountain, e.g.
`/tp @s 51085 600 -1087778`. Expect a pause while the land there is generated for the first time.

## Things that look odd but are right

- **The far north and south are stretched.** On a plain grid every degree of longitude is the same
  width, so land near the poles is wider than in reality. Greenland and Antarctica look huge.
  Distances along a line of latitude are only true at the equator.
- **Block 0, 0 is open sea**, in the Gulf of Guinea.
- **Sea level is Y 160** with the default settings, not 63.

## A quick converter

Python:

```python
SCALE = 5
BPD = 40_075_016.686 / SCALE / 360
def to_blocks(lat, lon): return round(lon * BPD), round(-lat * BPD)
def to_latlon(x, z): return -z / BPD, x / BPD
print(to_blocks(48.8584, 2.2945))   # (51085, -1087778)
```
