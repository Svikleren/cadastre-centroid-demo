package lv.cadastre.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.geotools.api.referencing.crs.ProjectedCRS;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.OperationNotFoundException;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygonal;

public class CadastreImporter {
    public List<CadastreCentroid> importInput(Path input) throws IOException {
        try (var prepared = CadastreInput.prepare(input)) {
            return importFiles(prepared.shapefiles());
        }
    }

    public List<CadastreCentroid> importDirectory(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Cadastral root must be an existing directory: " + root);
        }
        List<Path> files;
        try (var walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase("KKBuilding.shp"))
                    .sorted().toList();
        }
        return importFiles(files);
    }

    private List<CadastreCentroid> importFiles(List<Path> files) {
        System.out.println("Found " + files.size() + " KKBuilding shapefiles");
        var results = new ArrayList<CadastreCentroid>();
        long totalSkipped = 0;
        int failed = 0;
        for (Path file : files) {
            System.out.println("Processing: " + file.toAbsolutePath());
            ShapefileDataStore store = null;
            int processed = 0;
            int skipped = 0;
            try {
                store = new ShapefileDataStore(file.toUri().toURL());
                var source = store.getFeatureSource();
                var crs = source.getSchema().getCoordinateReferenceSystem();
                if (crs == null) crs = ShapefileCrs.readProjection(file);
                System.out.println("Source CRS: " + CRS.toSRS(crs) + " / " + crs.getName());
                if (!(crs instanceof ProjectedCRS)) {
                    throw new IllegalArgumentException("Source CRS must be projected for polygon centroid calculation");
                }
                var codeDescriptor = source.getSchema().getDescriptor("CODE");
                if (codeDescriptor == null || codeDescriptor.getType().getBinding() != String.class) {
                    throw new IllegalArgumentException("CODE must be a String attribute; numeric identifiers are not accepted");
                }
                // Longitude-first target: transformed X is longitude and Y is latitude.
                var target = CRS.decode("EPSG:4326", true);
                MathTransform transform;
                try {
                    transform = CRS.findMathTransform(crs, target, false);
                } catch (OperationNotFoundException ex) {
                    // ESRI .prj files may omit datum shift metadata. GeoTools' lenient
                    // mode still uses the detected projection, but permits zero datum shift.
                    System.err.println("CRS warning for " + file + ": " + ex.getMessage()
                            + "; using lenient transformation (missing datum shift assumed zero)");
                    transform = CRS.findMathTransform(crs, target, true);
                }
                String group = file.toAbsolutePath().getParent().getFileName().toString();
                try (SimpleFeatureIterator iterator = source.getFeatures().features()) {
                    while (iterator.hasNext()) {
                        // Iterator/DBF read failures are file-level: retrying may never advance.
                        var feature = iterator.next();
                        try {
                            var code = (String) feature.getAttribute("CODE");
                            var geometry = (Geometry) feature.getDefaultGeometry();
                            if (code == null || code.isBlank() || geometry == null || geometry.isEmpty()) {
                                skipped++;
                                continue;
                            }
                            if (!(geometry instanceof Polygonal)) throw new IllegalArgumentException("Expected polygon geometry");
                            results.add(toCentroid(code, geometry, transform, group));
                            processed++;
                        } catch (Exception ex) {
                            skipped++;
                            System.err.println("Bad feature " + feature.getID() + " in " + file + ": " + ex);
                        }
                    }
                }
            } catch (Exception ex) {
                failed++;
                System.err.println("Failed shapefile " + file + ": " + ex);
            } finally {
                if (store != null) store.dispose();
            }
            totalSkipped += skipped;
            System.out.println("Features processed: " + processed + "\nFeatures skipped: " + skipped);
        }
        System.out.println("Total buildings: " + results.size() + "\nTotal skipped: " + totalSkipped
                + "\nFailed shapefiles: " + failed);
        return results;
    }

    static CadastreCentroid toCentroid(String code, Geometry geometry, MathTransform transform,
                                       String sourceGroup) throws Exception {
        Point centroid = geometry.getCentroid(); // In projected source CRS, BEFORE transformation.
        Point transformed = (Point) JTS.transform(centroid, transform);
        double longitude = transformed.getX();
        double latitude = transformed.getY();
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
            throw new IllegalArgumentException("Invalid WGS84 coordinate");
        }
        return new CadastreCentroid(code, latitude, longitude, sourceGroup);
    }
}
