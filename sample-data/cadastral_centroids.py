import sys
import zipfile
from pathlib import Path

import geopandas as gpd
import pandas as pd


SHAPEFILE_NAME = "KKBuilding.shp"
TARGET_EPSG = 4326
DEFAULT_OUTPUT_FILE = "building_centroids.csv"
DUPLICATES_FILE = "duplicate_codes.csv"


def process_shapefile(zip_path: Path, shp_name: str):
    print(f"Processing: {shp_name}")

    # GDAL virtual filesystem allows GeoPandas to read a Shapefile
    # directly from ZIP without extracting it first.
    gdal_path = (
        f"/vsizip/{zip_path.resolve().as_posix()}/{shp_name}"
    )

    try:
        gdf = gpd.read_file(gdal_path)
    except Exception as e:
        print(f"  ERROR reading Shapefile: {e}")
        return None

    print(f"  Objects: {len(gdf)}")
    print(f"  CRS: {gdf.crs}")

    if "CODE" not in gdf.columns:
        print("  SKIPPED: CODE column not found")
        return None

    if gdf.crs is None:
        print("  SKIPPED: CRS is missing")
        return None

    # Keep only records with usable geometry.
    valid = gdf[
        gdf.geometry.notna()
        & ~gdf.geometry.is_empty
    ].copy()

    skipped = len(gdf) - len(valid)

    if skipped:
        print(f"  Skipped invalid geometries: {skipped}")

    if valid.empty:
        print("  SKIPPED: no valid geometries")
        return None

    # IMPORTANT:
    # Calculate centroid in the original projected CRS.
    #
    # Do NOT transform the polygon to WGS84 first.
    centroids = valid.geometry.centroid

    centroid_gdf = gpd.GeoDataFrame(
        {
            "CODE": valid["CODE"].astype(str)
        },
        geometry=centroids,
        crs=gdf.crs
    )

    # Transform only the centroid points to WGS84.
    centroid_gdf = centroid_gdf.to_crs(
        epsg=TARGET_EPSG
    )

    # Example:
    #
    # ExportCadGroup_<group>/KKBuilding.shp
    #
    # becomes:
    #
    # ExportCadGroup_<group>
    source_group = Path(shp_name).parent.name

    result = pd.DataFrame({
        "CODE": centroid_gdf["CODE"],
        "LATITUDE": centroid_gdf.geometry.y,
        "LONGITUDE": centroid_gdf.geometry.x,
        "SOURCE_GROUP": source_group
    })

    print(f"  OK: {len(result)} centroids")

    return result


def find_building_shapefiles(zip_path: Path):
    try:
        with zipfile.ZipFile(zip_path, "r") as archive:
            return sorted(
                name
                for name in archive.namelist()
                if not name.endswith("/")
                and Path(name).name.lower()
                == SHAPEFILE_NAME.lower()
            )

    except zipfile.BadZipFile as e:
        raise RuntimeError(
            f"Invalid ZIP file: {zip_path}"
        ) from e


def main():
    if len(sys.argv) not in (2, 3):
        print(
            "Usage:\n"
            "  python cadastral_centroids.py <cadaster.zip>\n"
            "\n"
            "Optional custom output file:\n"
            "  python cadastral_centroids.py "
            "<cadaster.zip> <output.csv>",
            file=sys.stderr
        )
        sys.exit(1)

    zip_path = Path(sys.argv[1])

    output_file = (
        Path(sys.argv[2])
        if len(sys.argv) == 3
        else Path(DEFAULT_OUTPUT_FILE)
    )

    if not zip_path.exists():
        print(
            f"ERROR: ZIP file not found: {zip_path}",
            file=sys.stderr
        )
        sys.exit(1)

    if not zip_path.is_file():
        print(
            f"ERROR: Not a file: {zip_path}",
            file=sys.stderr
        )
        sys.exit(1)

    print(f"ZIP: {zip_path.resolve()}")
    print(f"Output: {output_file.resolve()}")
    print()

    shapefiles = find_building_shapefiles(
        zip_path
    )

    print(
        f"Found {len(shapefiles)} "
        f"{SHAPEFILE_NAME} files"
    )
    print()

    if not shapefiles:
        print("Nothing to process")
        sys.exit(0)

    results = []
    failed_files = 0

    for shp_name in shapefiles:
        result = process_shapefile(
            zip_path,
            shp_name
        )

        if result is not None:
            results.append(result)
        else:
            failed_files += 1

    if not results:
        print(
            "ERROR: No data produced",
            file=sys.stderr
        )
        sys.exit(1)

    # Combine all cadastral groups.
    output = pd.concat(
        results,
        ignore_index=True
    )

    print()
    print("================================")
    print("PROCESSING SUMMARY")
    print("================================")
    print(f"Shapefiles found:  {len(shapefiles)}")
    print(f"Shapefiles failed: {failed_files}")
    print(f"Total records:      {len(output)}")
    print("================================")

    # Check CODE for duplicates.
    duplicated = output[
        output.duplicated(
            subset=["CODE"],
            keep=False
        )
    ]

    if not duplicated.empty:
        print()
        print(
            f"WARNING: {len(duplicated)} rows "
            f"have duplicated CODE"
        )

        duplicated.to_csv(
            DUPLICATES_FILE,
            index=False
        )

        print(
            f"Duplicates saved to: "
            f"{Path(DUPLICATES_FILE).resolve()}"
        )
    else:
        print()
        print("No duplicate CODE values found")

    # Write final result.
    output.to_csv(
        output_file,
        index=False
    )

    print()
    print(f"Saved: {output_file.resolve()}")


if __name__ == "__main__":
    try:
        main()

    except KeyboardInterrupt:
        print(
            "\nInterrupted by user",
            file=sys.stderr
        )
        sys.exit(130)

    except Exception as e:
        print(
            f"FATAL ERROR: {e}",
            file=sys.stderr
        )
        sys.exit(1)