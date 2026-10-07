package lv.cadastre.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.referencing.CRS;

final class ShapefileCrs {
    private static final Pattern BARE_NAME = Pattern.compile(
            "(?<=[\\[,])\\s*([\\p{L}_][\\p{L}\\p{N}_ .+\\-/]*?)\\s*(?=[,\\]])");
    private static final Pattern AUTHORITY_CODE = Pattern.compile(
            "(AUTHORITY\\[\"[^\"]+\",\\s*)([0-9]+)(\\])", Pattern.CASE_INSENSITIVE);

    static CoordinateReferenceSystem readProjection(Path shapefile) throws Exception {
        Path projection;
        try (var files = Files.list(shapefile.toAbsolutePath().getParent())) {
            projection = files.filter(p -> p.getFileName().toString().equalsIgnoreCase("KKBuilding.prj"))
                    .findFirst().orElseThrow(() -> new IOException("Missing source CRS (.prj required): " + shapefile));
        }
        String wkt = Files.readString(projection, StandardCharsets.UTF_8).replace("\uFEFF", "").strip();
        try {
            return CRS.parseWKT(wkt);
        } catch (Exception original) {
            // VZD's October 2026 export omits every WKT string quotation mark.
            // Repair syntax only: retain its declared datum, projection, units and parameters.
            if (wkt.contains("\"")) throw original;
            String repaired = repairUnquotedWkt(wkt);
            try {
                var crs = CRS.parseWKT(repaired);
                System.err.println("CRS syntax warning for " + projection
                        + ": recovered unquoted WKT names; source definition preserved");
                return crs;
            } catch (Exception ex) {
                ex.addSuppressed(original);
                throw ex;
            }
        }
    }

    static String repairUnquotedWkt(String wkt) {
        String names = BARE_NAME.matcher(wkt).replaceAll("\"$1\"");
        return AUTHORITY_CODE.matcher(names).replaceAll("$1\"$2\"$3");
    }
}
