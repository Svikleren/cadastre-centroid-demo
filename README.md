# Latvian cadastral centroid PoC

A standalone Java application using GeoTools and JTS. It recursively reads only
`KKBuilding.shp`, preserves `CODE` as a String (including leading zeros), and
returns latitude, longitude and the Shapefile parent directory name. The existing
IntelliJ starter `src/Main.java` is preserved but is outside Gradle's source sets.

Application code lives in `sources/lv/cadastre/demo` and tests in
`tests/lv/cadastre/demo`. Gradle and IntelliJ use `sources` and `tests` as their
source roots, so the `lv.cadastre.demo` package matches the directory structure.

## Requirements and build

Install **JDK 21** and set `JAVA_HOME` to it. No Gradle installation is required:
the checked-in Gradle 9.8.0 wrapper downloads Gradle on first use. Initial builds
need internet access to Maven Central and the official OSGeo release repository.
GeoTools 35.1 uses `gt-shapefile`, `gt-referencing` and `gt-epsg-hsql`; feature
access comes through the transitive `gt-main` dependency. JTS is 1.20.0.

Unix/macOS:

```sh
chmod +x gradlew
./gradlew clean build
./gradlew run
```

Windows PowerShell (adjust JDK path if necessary):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\gradlew.bat clean build
.\gradlew.bat run
```

Windows cmd also supports `gradlew.bat run`.
The application always reads `sample-data` relative to the working directory.
Run from the project root; in IntelliJ use `$PROJECT_DIR$` as the working
directory and leave program arguments empty. The application always writes
`result.csv` in the working directory and prints its absolute path after export,
along with totals and the first 20 records. Command-line arguments are unused.
Missing input directory or CSV write failures exit with status 1.

## Input

```text
sample-data/
  ExportCadGroup_0100111/
    KKBuilding.shp
    KKBuilding.shx
    KKBuilding.dbf
    KKBuilding.prj
    KKBuilding.cpg
  ExportCadGroup_0100126/
    KKBuilding.shp
    ... companion files ...
```

Other layers are ignored. `.prj` must describe a projected CRS; it is detected
from the schema and logged, rather than blindly assuming EPSG:3059. A strict
transformation is attempted first. If GeoTools cannot find that operation (the
provided ESRI `.prj` omits Bursa-Wolf datum parameters), the application logs a
warning and tries GeoTools' lenient transformation, which permits a zero shift
for missing datum metadata. The detected projection and axes are still used.
This approximation is validated against the supplied Latvia control value;
other datums may require authoritative transformation metadata for accuracy. Missing CRS
or non-String CODE schemas fail that file clearly. Null/blank CODE and null/empty
geometry are skipped. Feature conversion errors are logged and processing
continues. Corrupt iterator reads stop that file, since the iterator may no longer
advance safely; subsequent files are still processed. DataStores, feature
iterators, directory streams and CSV writers are closed/disposed.

## GIS calculation and acceptance check

JTS `geometry.getCentroid()` runs in the source projected CRS (normally
EPSG:3059, LKS-92 / Latvia TM). Only that Point is transformed. Transforming the
whole polygon first would calculate a different centroid in angular coordinates.
GeoTools decodes EPSG:4326 with longitude-first axis order: X is longitude and
Y is latitude. No manual coordinate swapping is used.

For real building **01001260033002**, the control source centroid is approximately
X = 514896.51737950393, Y = 304729.99573373026. Expected WGS84:

- Latitude: **56.88606546818168**
- Longitude: **24.244472425243256**

Tests use a tolerance of 1e-6 degrees. The integration test reads the real
`sample-data/ExportCadGroup_0100126` directory. If absent, JUnit explicitly marks
the test **skipped**, with a reason; it does not report a passed acceptance check.
Place that real directory and its companion files there, or choose a root:

```sh
./gradlew test -Dcadastre.test.root=/data/cadaster
```

```powershell
.\gradlew.bat test '-Dcadastre.test.root=C:\data\cadaster'
```

When data is present, missing acceptance CODE is a test failure. Unit tests also
check the known source coordinate independently, centroid calculation order,
root validation, and UTF-8 CSV escaping. No synthetic Shapefile is used.

Validated with the included real data: `gradlew.bat clean build` passed all five
tests. A CLI run over `sample-data` read 127 building Shapefiles and exported
103,166 records, with zero skipped features and zero failed files. Java produced
latitude **56.88606546729516**, longitude **24.2444724252431** for the acceptance
building, within the specified tolerance.

## CSV and scope

CSV columns are `CODE,LATITUDE,LONGITUDE,SOURCE_GROUP`; values containing commas,
quotes or newlines are quoted correctly. Output is UTF-8 with CRLF record endings
and decimal points independent of locale. Spreadsheet software may interpret
CODE numerically: import that column explicitly as text to retain leading zeros.
An existing output CSV is overwritten.

This small PoC holds all results in memory and reports file failures while
returning successfully processed records. A run with partial failures still
exports those records; inspect the failure summary. Skipped counts cover known
feature skips, not unreadable rows remaining in a corrupt file. A polygon centroid
can lie outside a concave building; this is the requested centroid, not an interior
point. Production integration, database architecture and large-scale streaming
are intentionally outside this PoC's scope.
