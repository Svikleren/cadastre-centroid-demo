package lv.cadastre.demo;

import java.nio.file.Path;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import static org.junit.jupiter.api.Assertions.*;

class CadastreImporterTest {
    @TempDir Path temp;

    @Test void validatesRootAndHandlesEmptyDirectory() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new CadastreImporter().importDirectory(temp.resolve("missing")));
        assertTrue(new CadastreImporter().importDirectory(temp).isEmpty());
    }

    @Test void knownProjectedCentroidTransformsWithCorrectAxisOrder() throws Exception {
        var point = new GeometryFactory().createPoint(new Coordinate(514896.51737950393, 304729.99573373026));
        var transform = CRS.findMathTransform(CRS.decode("EPSG:3059", true), CRS.decode("EPSG:4326", true), false);
        var result = CadastreImporter.toCentroid("01001260033002", point, transform, "control");
        assertEquals("01001260033002", result.code());
        assertEquals(56.88606546818168, result.latitude(), 1e-6);
        assertEquals(24.244472425243256, result.longitude(), 1e-6);
    }

    @Test void centroidIsCalculatedInSourceCoordinates() throws Exception {
        var polygon = new GeometryFactory().createPolygon(new Coordinate[] {
                new Coordinate(400000, 200000), new Coordinate(650000, 200000),
                new Coordinate(400000, 450000), new Coordinate(400000, 200000)});
        var transform = CRS.findMathTransform(CRS.decode("EPSG:3059", true), CRS.decode("EPSG:4326", true), false);
        var expected = JTS.transform(polygon.getCentroid(), transform).getCoordinate();
        var result = CadastreImporter.toCentroid("00001", polygon, transform, "unit");
        assertEquals(expected.x, result.longitude(), 1e-10);
        assertEquals(expected.y, result.latitude(), 1e-10);
        var wrong = JTS.transform(polygon, transform).getCentroid();
        assertTrue(Math.abs(wrong.getY() - result.latitude()) > 1e-6);
    }
}
