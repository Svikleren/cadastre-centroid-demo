package lv.cadastre.demo;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RealCadastreIntegrationTest {
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
