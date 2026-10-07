package lv.cadastre.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

/** Prepares loose Shapefiles and ZIP archives; owns only its temporary extraction directory. */
public final class CadastreInput implements AutoCloseable {
    private final Path temporaryRoot;
    private final List<Path> shapefiles = new ArrayList<>();

    private CadastreInput(Path temporaryRoot) {
        this.temporaryRoot = temporaryRoot;
    }

    public static CadastreInput prepare(Path input) throws IOException {
        List<Path> files;
        if (Files.isDirectory(input)) {
            try (var walk = Files.walk(input)) {
                files = walk.filter(Files::isRegularFile).sorted().toList();
            }
        } else if (Files.isRegularFile(input) && isZip(input)) {
            files = List.of(input);
        } else {
            throw new IllegalArgumentException("Input must be an existing directory or ZIP file: " + input);
        }
        var prepared = new CadastreInput(Files.createTempDirectory("cadastre-unzip-"));
        try {
            prepared.shapefiles.addAll(files.stream().filter(CadastreInput::isBuilding).toList());
            var archives = files.stream().filter(CadastreInput::isZip).toList();
            System.out.println("Found " + archives.size() + " ZIP archives");
            int failed = 0;
            for (int i = 0; i < archives.size(); i++) {
                Path archive = archives.get(i);
                String name = archive.getFileName().toString();
                // A flat ExportCadGroup_*.zip keeps its group name as the .shp parent.
                Path destination = prepared.temporaryRoot.resolve(Integer.toString(i))
                        .resolve(name.substring(0, name.length() - 4));
                try {
                    System.out.println("Extracting ZIP: " + archive.toAbsolutePath());
                    extract(archive, destination);
                    try (var walk = Files.walk(destination)) {
                        prepared.shapefiles.addAll(walk.filter(Files::isRegularFile)
                                .filter(CadastreInput::isBuilding).sorted().toList());
                    }
                } catch (IOException | RuntimeException ex) {
                    failed++;
                    System.err.println("Failed ZIP " + archive + ": " + ex);
                    // Do not process partially extracted archives.
                    deleteTree(destination);
                }
            }
            System.out.println("Failed ZIP archives: " + failed);
            return prepared;
        } catch (IOException | RuntimeException ex) {
            try { prepared.close(); } catch (IOException cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
    }

    public List<Path> shapefiles() {
        return List.copyOf(shapefiles);
    }

    private static boolean isBuilding(Path path) {
        return path.getFileName().toString().equalsIgnoreCase("KKBuilding.shp");
    }

    private static boolean isZip(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    static void extract(Path archive, Path destination) throws IOException {
        Path root = destination.toAbsolutePath().normalize();
        Files.createDirectories(root);
        try (var zip = new ZipFile(archive.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                Path target = root.resolve(name).normalize();
                if (!target.startsWith(root) || name.startsWith("/") || name.contains(":")) {
                    throw new IOException("Unsafe ZIP entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (var stream = zip.getInputStream(entry)) {
                        Files.copy(stream, target);
                    }
                }
            }
        }
    }

    private static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        List<Path> paths;
        try (var walk = Files.walk(directory)) {
            paths = walk.sorted(Comparator.reverseOrder()).toList();
        }
        IOException failure = null;
        for (Path path : paths) {
            try { Files.deleteIfExists(path); }
            catch (IOException ex) {
                if (failure == null) failure = ex;
                else failure.addSuppressed(ex);
            }
        }
        if (failure != null) throw failure;
    }

    @Override public void close() throws IOException {
        deleteTree(temporaryRoot);
    }
}
