package com.convert2web;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.awt.geom.Point2D;

public class BatikGraphicsHandlerTest {

    @Test
    public void testMoveToStartsNewPath() {
        BatikGraphicsHandler handler = new BatikGraphicsHandler();
        handler.initialize(new Point2D.Double(0, 0), new Point2D.Double(100, 100), 100, 100);

        // Simulate a moveTo command
        handler.moveTo(10, 10);
        assertNotNull(handler.getCurrentPoint(), "Current point should not be null after moveTo.");
        assertEquals(new Point2D.Double(10, 10), handler.getCurrentPoint(), "Current point should be (10, 10).");

        // Simulate another moveTo command which should start a new path
        handler.moveTo(20, 20);
        assertNotNull(handler.getCurrentPoint(), "Current point should not be null after second moveTo.");
        assertEquals(new Point2D.Double(20, 20), handler.getCurrentPoint(), "Current point should be (20, 20) after starting new path.");
    }
} 