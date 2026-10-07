package lv.cadastre.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CsvExporter {
    public void export(Path target, List<CadastreCentroid> records) throws IOException {
        try (var writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write("CODE,LATITUDE,LONGITUDE,SOURCE_GROUP\r\n");
            for (var record : records) {
                writer.write(escape(record.code()) + "," + record.latitude() + "," + record.longitude()
                        + "," + escape(record.sourceGroup()) + "\r\n");
            }
        }
    }

    static String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
