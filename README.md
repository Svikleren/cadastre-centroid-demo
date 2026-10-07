# Latvian Cadastral Centroid PoC

A standalone Java proof of concept for extracting building centroids from the Latvian State Land Service (Valsts zemes dienests, VZD) open cadastral spatial dataset.

The application uses **GeoTools** and **JTS** to:

1. recursively discover `KKBuilding.shp` files;
2. read building cadastral identifiers and geometries;
3. calculate building centroids in the source projected coordinate reference system;
4. transform the resulting centroid points to WGS84 (`EPSG:4326`);
5. export the results to CSV.

The implementation is pure Java and does not require Python, GeoPandas, QGIS, a database, or an external GIS service.

## Technology

- Java 21
- Gradle 9.8
- GeoTools 35.1
- JTS 1.20.0
- JUnit 5

The relevant GeoTools modules are:

- `gt-shapefile`
- `gt-referencing`
- `gt-epsg-hsql`

Feature support is provided transitively through `gt-main`.

GeoTools dependencies are obtained from the official OSGeo Maven repository in addition to Maven Central.

## Data source

The application is designed for the open cadastral spatial data published by the Latvian State Land Service (Valsts zemes dienests, VZD):

https://data.gov.lv/dati/lv/dataset/kadastra-informacijas-sistemas-atverti-telpiskie-dati

Download and extract the required cadastral data separately.

Real cadastral datasets are intentionally **not included in this repository**.

A typical input structure looks like:

```text
sample-data/
  ExportCadGroup_<group-id>/
    KKBuilding.shp
    KKBuilding.shx
    KKBuilding.dbf
    KKBuilding.prj
    KKBuilding.cpg

  ExportCadGroup_<another-group-id>/
    KKBuilding.shp
    KKBuilding.shx
    KKBuilding.dbf
    KKBuilding.prj
    KKBuilding.cpg
```

Only files named:

```text
KKBuilding.shp
```

are processed.

Other cadastral layers are ignored.

## Building identifier

The `CODE` attribute from `KKBuilding` is used as the building cadastral identifier.

`CODE` is always preserved as a `String`, including leading zeros.

The resulting model is:

```java
public record CadastreCentroid(
        String code,
        double latitude,
        double longitude,
        String sourceGroup
) {}
```

`sourceGroup` contains the name of the directory from which the Shapefile was read.

## GIS calculation

Latvian cadastral spatial data normally uses:

```text
EPSG:3059
LKS-92 / Latvia TM
```

The application does not blindly assume the CRS. The source CRS is obtained from the Shapefile schema and `.prj` metadata.

The centroid is deliberately calculated **before** transformation to WGS84:

```text
Building polygon
in source projected CRS
        |
        v
JTS geometry.getCentroid()
        |
        v
Centroid in source CRS
        |
        v
GeoTools MathTransform
        |
        v
EPSG:4326
```

This is important because calculating a polygon centroid after transforming the polygon into geographic longitude/latitude coordinates can produce a different result.

Only the resulting centroid point is transformed to WGS84.

For the final WGS84 coordinate:

```text
X = longitude
Y = latitude
```

GeoTools is configured to use longitude-first axis order for `EPSG:4326`.

Coordinates are not manually swapped.

## CRS transformation

A strict GeoTools transformation is attempted first.

Some source `.prj` definitions may not contain sufficient datum transformation metadata for a strict transformation. If a strict operation cannot be constructed, the application logs a warning and attempts a lenient GeoTools transformation.

The detected projection and coordinate axes are still used.

The lenient fallback should not be assumed to be appropriate for arbitrary coordinate reference systems. Production use with other datums may require authoritative transformation metadata.

## Validation

The Java implementation was validated independently against a reference implementation using **GeoPandas**.

A complete test dataset contained:

```text
127 KKBuilding Shapefiles
103,166 building records
```

Both implementations produced:

```text
103,166 unique cadastral identifiers
127 source groups
0 duplicate CODE values
```

The cadastral identifiers and source groups matched exactly.

The maximum observed coordinate difference between the Java/GeoTools and Python/GeoPandas implementations was approximately:

```text
latitude:   8.87e-10 degrees
longitude:  2.14e-13 degrees
```

This corresponds to approximately **0.1 mm** at the tested latitude and is negligible for the intended use case.

An optional integration test validates the Java transformation against coordinates calculated independently with GeoPandas using real data from the public cadastral dataset.

Tests use a tolerance of:

```text
1e-6 degrees
```

If the required real test data is not present, the integration test is explicitly reported as **skipped** rather than passed.

Real cadastral test data is not included in this repository.

## Build

### Requirements

Install JDK 21 and ensure `JAVA_HOME` points to it.

A separate Gradle installation is not required because the Gradle Wrapper is included.

Initial dependency resolution requires access to:

- Maven Central
- the official OSGeo release repository

### Unix / macOS

```bash
chmod +x gradlew
./gradlew clean build
```

### Windows PowerShell

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\gradlew.bat clean build
```

## Run

The application reads cadastral data from:

```text
sample-data/
```

relative to the current working directory.

Run the application from the project root.

### Unix / macOS

```bash
./gradlew run
```

### Windows PowerShell

```powershell
.\gradlew.bat run
```

The application prints:

- discovered `KKBuilding.shp` files;
- detected source CRS information;
- processing statistics;
- skipped features;
- failed Shapefiles;
- total number of processed buildings;
- the first 20 resulting records.

It also creates:

```text
result.csv
```

in the current working directory.

## CSV output

The output format is:

```text
CODE,LATITUDE,LONGITUDE,SOURCE_GROUP
```

For example:

```text
CODE,LATITUDE,LONGITUDE,SOURCE_GROUP
<building-code>,<latitude>,<longitude>,<source-group>
```

The file is UTF-8 encoded.

CSV values containing commas, quotes, or newlines are escaped according to CSV quoting rules.

Decimal formatting is independent of the system locale.

`CODE` should be imported into spreadsheet applications explicitly as text to preserve leading zeros.

An existing `result.csv` is overwritten.

## Tests

Run the test suite with:

### Unix / macOS

```bash
./gradlew test
```

### Windows PowerShell

```powershell
.\gradlew.bat test
```

A real cadastral dataset can optionally be supplied to the integration test.

### Unix / macOS

```bash
./gradlew test -Dcadastre.test.root=/path/to/cadaster
```

### Windows PowerShell

```powershell
.\gradlew.bat test '-Dcadastre.test.root=C:\path\to\cadaster'
```

If the expected acceptance record cannot be found in a supplied real dataset, the integration test fails.

If no real test dataset is available, the integration test is explicitly marked as skipped.

The test suite also covers logic that can be verified without distributing real cadastral Shapefiles.

No synthetic Shapefile is used to satisfy the real-data integration test.

## Error handling

Invalid individual features do not stop the complete import.

Features are skipped when:

- `CODE` is null or blank;
- geometry is null;
- geometry is empty.

Feature conversion errors are logged and processing continues.

A corrupt feature iterator stops processing of the affected Shapefile because continuing to advance an invalid iterator may not be safe. Other Shapefiles continue to be processed.

Missing CRS information and incompatible schemas are reported as file-level failures.

Resources including DataStores, feature iterators, directory streams, and CSV writers are explicitly closed or disposed.

## Dependency security

The project uses a recent Jackson BOM to override the older Jackson Core version requested transitively by GeoTools.

The effective runtime dependency version should be verified with:

```bash
./gradlew dependencyInsight \
    --dependency jackson-core \
    --configuration runtimeClasspath
```

On Windows PowerShell:

```powershell
.\gradlew.bat dependencyInsight --dependency jackson-core --configuration runtimeClasspath
```

Dependency versions should be reviewed and updated as part of normal maintenance before production use.

## Limitations

This project is intentionally a small proof of concept.

In particular:

- all resulting records are currently retained in memory;
- output is written to CSV rather than a database;
- partial Shapefile failures do not prevent successfully processed records from being exported;
- skipped counts cover known feature skips and do not represent unreadable records remaining after a corrupt iterator;
- a geometric polygon centroid may lie outside a highly concave building footprint;
- the implementation calculates the requested geometric centroid, not an interior representative point;
- the lenient CRS transformation fallback is intended for the tested cadastral data and should not automatically be assumed appropriate for unrelated datasets;
- production database integration is outside the scope of this project;
- production scheduling, streaming, monitoring, and deployment architecture are outside the scope of this project.

The purpose of the PoC is to demonstrate and validate the following Java GIS processing pipeline:

```text
Latvian cadastral Shapefile
        |
        v
GeoTools
        |
        v
JTS centroid in projected CRS
        |
        v
GeoTools CRS transformation
        |
        v
WGS84 latitude / longitude
```