package lv.cadastre.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CadastreInputTest {
    @TempDir Path temp;

    private Path archive(String entry) throws IOException {
        Path archive = temp.resolve("data.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write("Archive extraction test".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return archive;
    }

    @Test void extractsNestedFilesAndClosesArchive() throws Exception {
        Path archive = archive("nested/readme.txt");
        Path output = temp.resolve("output");
        CadastreInput.extract(archive, output);
        assertEquals("Archive extraction test", Files.readString(output.resolve("nested/readme.txt")));
        Files.delete(archive);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../escape.txt", "..\\escape.txt", "/escape.txt", "C:/escape.txt"})
    void rejectsEntriesOutsideDestination(String entry) throws Exception {
        assertThrows(IOException.class, () -> CadastreInput.extract(archive(entry), temp.resolve("output")));
        assertFalse(Files.exists(temp.resolve("escape.txt")));
    }

    @Test void logsAndSkipsInvalidArchive() throws Exception {
        Files.writeString(temp.resolve("broken.zip"), "Not a ZIP archive");
        try (var input = CadastreInput.prepare(temp)) {
            assertTrue(input.shapefiles().isEmpty());
        }
    }

    @Test void rejectsMissingInput() {
        assertThrows(IllegalArgumentException.class, () -> CadastreInput.prepare(temp.resolve("missing.zip")));
    }
}
