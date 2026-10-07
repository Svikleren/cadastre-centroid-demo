package lv.cadastre.demo;

import java.nio.file.Files;
import java.nio.file.Path;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ShapefileCrsTest {
    @TempDir Path temp;

    @Test void recoversUnquotedNewLatvianCrsWithoutReplacingItWith3059() throws Exception {
        String wkt = "PROJCS[LKS_2020_Latvia_TM,GEOGCS[GCS_LKS-2020,DATUM[D_LKS-2020,"
                + "SPHEROID[Geodetic_Reference_System_of_1980,6378137,298.2572221008916]],"
                + "PRIMEM[Greenwich,0],UNIT[Degree,0.017453292519943295]],PROJECTION[Transverse_Mercator],"
                + "PARAMETER[latitude_of_origin,0],PARAMETER[central_meridian,24],"
                + "PARAMETER[scale_factor,0.9996],PARAMETER[false_easting,500000],"
                + "PARAMETER[false_northing,-6000000],UNIT[Meter,1],AUTHORITY[EPSG,10306]]";
        Files.writeString(temp.resolve("KKBuilding.prj"), wkt);
        var crs = ShapefileCrs.readProjection(temp.resolve("KKBuilding.shp"));
        assertEquals("EPSG:10306", CRS.toSRS(crs));
        assertEquals("LKS_2020_Latvia_TM", crs.getName().getCode());
        assertEquals(CRS.AxisOrder.EAST_NORTH, CRS.getAxisOrder(crs));
        assertTrue(ShapefileCrs.repairUnquotedWkt(wkt).contains("AUTHORITY[\"EPSG\",\"10306\"]"));
    }

    @Test void stillRejectsMissingProjection() {
        assertThrows(java.io.IOException.class, () -> ShapefileCrs.readProjection(temp.resolve("KKBuilding.shp")));
    }
}
