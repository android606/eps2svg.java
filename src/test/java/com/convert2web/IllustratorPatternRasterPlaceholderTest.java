package com.convert2web;

import com.convert2web.model.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IllustratorPatternRasterPlaceholderTest {

    @Test
    void scanFindsRasterPatternDictionaryBBox() {
        String page = ""
                + "np\n"
                + "<<\n"
                + "/PaintType 1\n"
                + "/BBox [0 0 128 160 ]\n"
                + "/PaintProc { begin save\n"
                + "<< /T 1 /W 128 /H 160 >>\n"
                + "[\n<~abc~>\n]\n"
                + "end }\n"
                + ">>\n"
                + "f\n";

        List<BoundingBox> regions = IllustratorPatternRasterPlaceholder.scan(page);

        assertEquals(1, regions.size());
        assertEquals(0, regions.get(0).getLlx(), 0.001);
        assertEquals(0, regions.get(0).getLly(), 0.001);
        assertEquals(128, regions.get(0).getUrx(), 0.001);
        assertEquals(160, regions.get(0).getUry(), 0.001);
    }

    @Test
    void scanDedupesIdenticalBBoxes() {
        String tile = ""
                + "<< /PaintProc { <~x~> } /BBox [10 20 30 40 ] >>\n";
        List<BoundingBox> regions = IllustratorPatternRasterPlaceholder.scan(tile + tile);

        assertEquals(1, regions.size());
        assertEquals(10, regions.get(0).getLlx(), 0.001);
        assertEquals(20, regions.get(0).getLly(), 0.001);
    }

    @Test
    void scanIgnoresDictWithoutRasterMarker() {
        String page = "<< /Name (foo) /Data 1 >>\n";
        assertTrue(IllustratorPatternRasterPlaceholder.scan(page).isEmpty());
    }
}
