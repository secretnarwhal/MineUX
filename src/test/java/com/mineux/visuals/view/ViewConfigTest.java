package com.mineux.visuals.view;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The repair pass over view.json: the zoom's mode, the switch it replaced, and its ranges.
 */
class ViewConfigTest {

    @Test
    void theDefaultsAreTheOnesTheFileIsWrittenWith() {
        ViewConfig config = new ViewConfig();
        config.ensureDefaults();

        assertEquals(false, config.zoom, "zoom arrived switched on");
        assertEquals(false, config.clearWeather, "clear weather arrived switched on");
        assertEquals(4.0, config.zoomFactor);
    }

    // ---- the zoom's mode, and the switch it replaced ----

    private static ViewConfig read(String json) {
        ViewConfig config = new com.google.gson.Gson().fromJson(json, ViewConfig.class);
        config.ensureDefaults();
        return config;
    }

    @Test
    void theOldSmoothSwitchOnBecomesSmoothZoom() {
        ViewConfig config = read("{\"zoom\": true, \"zoomSmooth\": true}");
        assertEquals(ViewConfig.ZOOM_SMOOTH, config.zoomMode);
        assertEquals(null, config.zoomSmooth, "the old switch is still written out");
    }

    @Test
    void theOldSmoothSwitchOffBecomesInstant() {
        ViewConfig config = read("{\"zoom\": true, \"zoomSmooth\": false}");
        assertEquals(ViewConfig.ZOOM_INSTANT, config.zoomMode);
        assertEquals(null, config.zoomSmooth);
    }

    @Test
    void aFirstRunZoomsSmoothly() {
        assertEquals(ViewConfig.ZOOM_SMOOTH, read("{}").zoomMode);
        ViewConfig fresh = new ViewConfig();
        fresh.ensureDefaults();
        assertEquals(ViewConfig.ZOOM_SMOOTH, fresh.zoomMode);
    }

    @Test
    void aChosenModeWinsOverALeftoverSwitchAndAMisspeltOneIsTheDefault() {
        assertEquals(ViewConfig.ZOOM_INSTANT, read("{\"zoomMode\": \"instant\", \"zoomSmooth\": true}").zoomMode);
        assertEquals(ViewConfig.ZOOM_SMOOTH, read("{\"zoomMode\": \"smooth zoom\", \"zoomSmooth\": false}").zoomMode);
        assertEquals(ViewConfig.ZOOM_SMOOTH, read("{\"zoomMode\": \"smoooth\"}").zoomMode);
    }

    @Test
    void theMigratedFileNoLongerCarriesTheOldSwitch() {
        String written = new com.google.gson.Gson().toJson(read("{\"zoomSmooth\": true}"));
        assertTrue(!written.contains("zoomSmooth"), written);
        assertTrue(written.contains("\"zoomMode\":\"smooth zoom\""), written);
    }

    @Test
    void anImpossibleZoomTimeIsBroughtBackToTheSlider() {
        assertEquals(ViewConfig.MIN_ZOOM_TIME, read("{\"zoomTime\": 0}").zoomTime);
        assertEquals(ViewConfig.MAX_ZOOM_TIME, read("{\"zoomTime\": 60}").zoomTime);
    }
}
