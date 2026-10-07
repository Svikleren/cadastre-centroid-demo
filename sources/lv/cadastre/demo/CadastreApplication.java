package lv.cadastre.demo;

import java.nio.file.Path;

public class CadastreApplication {
    private static final Path CADASTRAL_ROOT = Path.of("sample-data");
    private static final Path CSV_OUTPUT = Path.of("result.csv");

    public static void main(String[] args) {
        try {
            System.out.println("Cadastral directory: " + CADASTRAL_ROOT.toAbsolutePath());
            var records = new CadastreImporter().importInput(CADASTRAL_ROOT);
            System.out.println("Buildings returned: " + records.size());
            records.stream().limit(20).forEach(System.out::println);
            new CsvExporter().export(CSV_OUTPUT, records);
            System.out.println("CSV written: " + CSV_OUTPUT.toAbsolutePath());
        } catch (Exception ex) {
            System.err.println("Application failed: " + ex);
            System.exit(1);
        }
    }
}
