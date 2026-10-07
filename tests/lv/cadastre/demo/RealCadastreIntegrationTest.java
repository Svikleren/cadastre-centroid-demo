package lv.cadastre.demo;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class RealCadastreIntegrationTest {
    @TempDir Path temp;

    @Test void importsDownloadedLks2020ArchiveAndExportsCsv() throws Exception {
        Path archive = Path.of("sample-data/0001000_kk_shp.zip");
        Assumptions.assumeTrue(Files.isRegularFile(archive), "Real downloaded 0001000_kk_shp.zip missing");
        var records = new CadastreImporter().importInput(archive);
        assertFalse(records.isEmpty(), "Real ZIP must produce buildings, not just a CSV header");
        var result = records.stream().filter(r -> r.code().equals("01001260033002"))
                .findFirst().orElseThrow(() -> new AssertionError("Control CODE missing from real ZIP"));
        assertEquals("ExportCadGroup_0100126", result.sourceGroup());
        // This archive declares LKS-2020, not the LKS-92 control dataset above.
        // Its coordinates must be in Latvia with longitude/latitude in correct order.
        assertTrue(result.latitude() > 56 && result.latitude() < 58);
        assertTrue(result.longitude() > 23 && result.longitude() < 26);
        Path csv = temp.resolve("result.csv");
        new CsvExporter().export(csv, records);
        try (var lines = Files.lines(csv)) {
            assertEquals(records.size() + 1L, lines.count());
        }
        System.out.println("Downloaded ZIP control result: " + result);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void verifiesRealBuildingFromZipAndCleansTemporaryFiles(boolean nested) throws Exception {
        Path root = Path.of(System.getProperty("cadastre.test.root"));
        Path group = root.getFileName().toString().equals("ExportCadGroup_0100126")
                ? root : root.resolve("ExportCadGroup_0100126");
        Assumptions.assumeTrue(Files.isDirectory(group), "Real ExportCadGroup_0100126 data missing");
        Path archive = temp.resolve("ExportCadGroup_0100126.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive)); var files = Files.list(group)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                zip.putNextEntry(new ZipEntry((nested ? "ExportCadGroup_0100126/" : "") + file.getFileName()));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
        Files.writeString(temp.resolve("broken.zip"), "Invalid ZIP");
        var records = new CadastreImporter().importInput(temp);
        var result = records.stream().filter(r -> r.code().equals("01001260033002"))
                .findFirst().orElseThrow(() -> new AssertionError("Acceptance CODE missing from ZIP"));
        assertEquals(56.88606546818168, result.latitude(), 1e-6);
        assertEquals(24.244472425243256, result.longitude(), 1e-6);
        assertEquals("ExportCadGroup_0100126", result.sourceGroup());
        System.out.println("ZIP acceptance result: " + result);
        Path extracted;
        try (var prepared = CadastreInput.prepare(archive)) {
            extracted = prepared.shapefiles().getFirst();
            assertTrue(Files.exists(extracted));
        }
        assertFalse(Files.exists(extracted));
        Files.delete(archive);
    }

    @Test void verifiesRealAcceptanceBuilding() throws Exception {
        Path root = Path.of(System.getProperty("cadastre.test.root"));
        Assumptions.assumeTrue(Files.isDirectory(root),
                "Real data missing: place ExportCadGroup_0100126 in sample-data or set -Dcadastre.test.root");
        var records = new CadastreImporter().importDirectory(root);
        var result = records.stream().filter(r -> r.code().equals("01001260033002"))
                .findFirst().orElseThrow(() -> new AssertionError("Acceptance CODE missing from real test data"));
        System.out.println("Real acceptance result: " + result);
        assertEquals(56.88606546818168, result.latitude(), 1e-6);
        assertEquals(24.244472425243256, result.longitude(), 1e-6);
        assertEquals("ExportCadGroup_0100126", result.sourceGroup());
    }
}
