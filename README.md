# Latvian Cadastral Building Centroids PoC

Java proof of concept for extracting building centroids from the Latvian State Land Service (VZD) open cadastral spatial data.

## What it does

The application:

1. recursively finds `KKBuilding.shp` files;
2. reads the building `CODE` and geometry;
3. calculates the polygon centroid in the source projected CRS;
4. transforms the centroid to WGS84 (`EPSG:4326`);
5. writes the result to `result.csv`.

```text
KKBuilding.shp
      ↓
source CRS
      ↓
JTS centroid
      ↓
GeoTools CRS transformation
      ↓
EPSG:4326
      ↓
result.csv
```

The source CRS is read from the Shapefile metadata and is not hardcoded.

Tested with:

- LKS-92 / Latvia TM (`EPSG:3059`)
- LKS-2020 / Latvia TM (`EPSG:10306`)

## Technology

- Java 21
- Gradle
- GeoTools
- JTS
- JUnit 5

## Input

Place the cadastral data under:

```text
sample-data/
  ExportCadGroup_<group>/
    KKBuilding.shp
    KKBuilding.shx
    KKBuilding.dbf
    KKBuilding.prj
    KKBuilding.cpg
```

Only `KKBuilding.shp` files are processed.

Real cadastral data is not included in this repository.

VZD open data:

https://data.gov.lv/dati/lv/dataset/kadastra-informacijas-sistemas-atverti-telpiskie-dati

## Output

```text
CODE,LATITUDE,LONGITUDE,SOURCE_GROUP
```

`CODE` is preserved as a `String` to keep leading zeros.

## Validation

The Java output was compared with an independent GeoPandas/PROJ implementation using complete real VZD datasets.

Both legacy `EPSG:3059` and current `EPSG:10306` datasets were tested.

For the current LKS-2020 dataset:

```text
102,396 buildings
102,396 matching CODE values
0 duplicate CODE values
0 SOURCE_GROUP mismatches
```

Maximum Java vs. GeoPandas coordinate difference:

```text
latitude:  ~8.87e-10 degrees
longitude: ~2.10e-13 degrees
```

## Build

Windows:

```powershell
.\gradlew.bat clean build
```

Linux/macOS:

```bash
./gradlew clean build
```

## Run

Windows:

```powershell
.\gradlew.bat run
```

Linux/macOS:

```bash
./gradlew run
```

The result is written to:

```text
result.csv
```