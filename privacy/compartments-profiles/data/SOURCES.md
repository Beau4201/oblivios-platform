# Sources of the persona data (ADR-208)

The two offline files of the Isolated persona, `places.tsv` and `cells.tsv`, are built by `../tools/build-persona-data.py` from two downloads. Built on 2026-11-01 from the two downloads below; the hashes of all inputs and outputs are in `PINS.sha256`. The builder never touches the network.

## Build of 2026-11-01

| Input | Source | Bytes | Note |
| --- | --- | --- | --- |
| `cities15000.zip` (34195 cities) | GeoNames, fetched by the assistant from `download.geonames.org/export/dump/` (public, no account) | 3 365 242 | GeoNames states the file changes daily; this is the copy of 2026-11-01 |
| `cell_towers.csv.gz` (5 535 038 cells, 3 675 566 of them LTE, 198 mobile country codes) | OpenCelliD export "full", downloaded by the owner with their own account | 120 320 455 | The export holds only cells observed in the last 18 months |

| Output | Lines | Bytes | Note |
| --- | --- | --- | --- |
| `places.tsv` | 33925 | 1 991 582 | 270 of the 34195 cities are left out by the rules of the builder; 191 countries |
| `cells.tsv` | 400374 | 11 707 497 | 22171 places have cells, 11754 have none (they are kept; the persona has no operator there) |

- The builder gave the same two files in two runs (byte-identical, `cmp`), the first in 491 s. `Places.parse` and `CellSubset.parse` of the package accept both files; every time zone is known to the JDK; every cell belongs to a listed place.
- Places without cells are concentrated where OpenCelliD has little LTE coverage near a city centre (for example India 3769, China 1023, Brazil 920 of the places). Every city of the Netherlands, Germany and France has cells; Spain (4 places), Italy (5) and the United Kingdom (3) have a few without. `Places.automaticFor` does not look at cells, so an Automatic place can be one without an operator; whether it should prefer places with cells is an open choice for the owner.
- The size is about 13.7 MB, close to the estimate of the design (about 25000 places, 15 MB).

## Licences read on 2026-10-30 (publishers' own pages)

| Data | Source | Licence as stated by the publisher | Terms that reach the image |
| --- | --- | --- | --- |
| Cities of at least 15000 inhabitants (`cities15000.txt`, 19 tab-separated columns) | GeoNames, `download.geonames.org/export/dump/` | Creative Commons Attribution 4.0 (the readme of the dumps) | Credit GeoNames with a link; commercial use is allowed; the data is provided as is. Not stated: redistribution or inclusion in a product. |
| Cell towers (CSV `radio,mcc,net,area,cell,unit,lon,lat,range,samples,...`) | OpenCelliD, `opencellid.org` | Creative Commons Attribution-ShareAlike 4.0 (`wiki.opencellid.org/wiki/Licensing`; the download page names no licence) | Credit: "Cell tower data from OpenCellID, licensed under CC BY-SA 4.0", with a link to the source and the licence, and a statement of the changes made. On an app screen that shows the data, the credit is shown there. Adapted material is shared under the same licence. A download needs an access token of an account that the owner creates; the CSV export holds only cells observed in the last 18 months. |

## Open for the owner before anything ships

- ShareAlike: `cells.tsv` is adapted material (a subset of at most 20 cells per place) and goes into the image under CC BY-SA 4.0, with its attribution in the image notices. Whether that reaches the rest of the image is a legal reading that the publishers' pages do not settle; the data files are separate files read only by the policy service, which is the argument that it does not. The plan stops here if the owner does not accept this reading (plan, Task 13 step 1).
- The page of the persona shows no cell data, so the attribution goes into the image notices only.
- GeoNames states no term for redistribution; the licence text of CC BY 4.0 allows it with attribution.
