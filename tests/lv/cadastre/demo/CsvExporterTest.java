package lv.cadastre.demo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CsvExporterTest {
    @TempDir Path temp;
    @Test void preservesLeadingZerosAndEscapesCsv() throws Exception {
        var file = temp.resolve("result.csv");
        new CsvExporter().export(file, List.of(new CadastreCentroid("00001", 56.8, 24.2, "a,\"b\"\nline")));
        assertEquals("CODE,LATITUDE,LONGITUDE,SOURCE_GROUP\r\n00001,56.8,24.2,\"a,\"\"b\"\"\nline\"\r\n",
                Files.readString(file));
    }
}
