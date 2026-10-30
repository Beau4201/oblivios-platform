# Sources of the persona data (ADR-208)

The two offline files of the Isolated persona, `places.tsv` and `cells.tsv`, are built by `../tools/build-persona-data.py` from two downloads. **Not built yet:** the owner has not supplied the files, so no size, source hash or output hash is recorded here. Nothing is downloaded by the assistant.

## Licences read on 2026-10-30 (publishers' own pages)

| Data | Source | Licence as stated by the publisher | Terms that reach the image |
| --- | --- | --- | --- |
| Cities of at least 15000 inhabitants (`cities15000.txt`, 19 tab-separated columns) | GeoNames, `download.geonames.org/export/dump/` | Creative Commons Attribution 4.0 (the readme of the dumps) | Credit GeoNames with a link; commercial use is allowed; the data is provided as is. Not stated: redistribution or inclusion in a product. |
| Cell towers (CSV `radio,mcc,net,area,cell,unit,lon,lat,range,samples,...`) | OpenCelliD, `opencellid.org` | Creative Commons Attribution-ShareAlike 4.0 (`wiki.opencellid.org/wiki/Licensing`; the download page names no licence) | Credit: "Cell tower data from OpenCellID, licensed under CC BY-SA 4.0", with a link to the source and the licence, and a statement of the changes made. On an app screen that shows the data, the credit is shown there. Adapted material is shared under the same licence. A download needs an access token of an account that the owner creates; the CSV export holds only cells observed in the last 18 months. |

## Open for the owner before anything ships

- ShareAlike: `cells.tsv` is adapted material (a subset of at most 20 cells per place) and goes into the image under CC BY-SA 4.0, with its attribution in the image notices. Whether that reaches the rest of the image is a legal reading that the publishers' pages do not settle; the data files are separate files read only by the policy service, which is the argument that it does not. The plan stops here if the owner does not accept this reading (plan, Task 13 step 1).
- The page of the persona shows no cell data, so the attribution goes into the image notices only.
- GeoNames states no term for redistribution; the licence text of CC BY 4.0 allows it with attribution.
