package com.sportage.obd

import com.sportage.obd.ui.boards.BoardDocument
import com.sportage.obd.ui.boards.BoardLayout
import com.sportage.obd.ui.boards.BoardLimits
import com.sportage.obd.ui.boards.BoardWidgetConfigs
import com.sportage.obd.ui.boards.BoardWidgetType
import com.sportage.obd.ui.boards.WidgetConfig
import com.sportage.obd.ui.boards.defaultWidgetConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Widget configuration, board document import/export, migration and limits. */
@RunWith(RobolectricTestRunner::class)
class WidgetConfigTest {

    @Test
    fun normalizationClampsAndOrdersValues() {
        val wild = WidgetConfig(
            titleOverride = "x".repeat(60),
            unitOverride = "u".repeat(30),
            minValue = 100f,
            maxValue = 10f,
            warnThreshold = 200f,
            criticalThreshold = 50f,
            decimals = 9,
            span = 7,
            refreshMs = 1,
            style = "holographic"
        ).normalized()
        assertEquals(10f, wild.minValue!!)
        assertEquals(100f, wild.maxValue!!)
        assertTrue(wild.warnThreshold!! <= wild.criticalThreshold!!)
        assertTrue(wild.warnThreshold in 10f..100f)
        assertEquals(WidgetConfig.MAX_TITLE, wild.titleOverride!!.length)
        assertEquals(WidgetConfig.MAX_UNIT, wild.unitOverride!!.length)
        assertEquals(2, wild.decimals)
        assertEquals(2, wild.span)
        assertEquals(WidgetConfig.MIN_REFRESH_MS, wild.refreshMs)
        assertEquals("auto", wild.style)
    }

    @Test
    fun validationAcceptsSaneConfigAndRejectsBadThresholds() {
        assertTrue(WidgetConfig(minValue = 0f, maxValue = 200f, warnThreshold = 140f, criticalThreshold = 170f)
            .validationErrors().isEmpty())
        assertFalse(WidgetConfig(minValue = 200f, maxValue = 10f).validationErrors().isEmpty())
        assertFalse(WidgetConfig(warnThreshold = 180f, criticalThreshold = 150f).validationErrors().isEmpty())
        assertFalse(WidgetConfig(decimals = 4).validationErrors().isEmpty())
        assertFalse(WidgetConfig(span = 5).validationErrors().isEmpty())
        assertFalse(WidgetConfig(refreshMs = 20).validationErrors().isEmpty())
        assertFalse(WidgetConfig(style = "unknown").validationErrors().isEmpty())
    }

    @Test
    fun configRoundTripsThroughJson() {
        val config = WidgetConfig(
            titleOverride = "Boost",
            unitOverride = "psi",
            minValue = 0f,
            maxValue = 30f,
            warnThreshold = 20f,
            criticalThreshold = 26f,
            decimals = 1,
            span = 2,
            refreshMs = 500,
            visible = false,
            style = "bar"
        )
        val restored = WidgetConfig.fromJson(config.toJson())
        assertEquals(config, restored)
    }

    @Test
    fun malformedConfigFallsBackToDefaults() {
        assertNull(WidgetConfig.fromJson(null))
        val restored = WidgetConfig.fromJson(JSONObject("{\"span\":99,\"refreshMs\":-4,\"decimals\":7}"))!!
        assertEquals(2, restored.span)
        assertEquals(WidgetConfig.MIN_REFRESH_MS, restored.refreshMs)
        assertEquals(2, restored.decimals)
    }

    @Test
    fun boardDocumentRoundTripPreservesTypesAndConfigs() {
        val layout = BoardLayout(
            listOf(
                listOf(BoardWidgetType.SPEED, BoardWidgetType.RPM, BoardWidgetType.MAP),
                listOf(BoardWidgetType.CLOCK)
            )
        )
        val configs = BoardWidgetConfigs(
            listOf(
                listOf(
                    WidgetConfig(titleOverride = "KMH", span = 2, warnThreshold = 130f),
                    WidgetConfig(decimals = 0),
                    WidgetConfig(span = 2)
                ),
                listOf(WidgetConfig(visible = false))
            )
        )
        val document = BoardDocument.export(layout, configs)
        val decoded = BoardDocument.import(document)
        assertNotNull(decoded)
        assertEquals(layout, decoded!!.layout)
        assertEquals(configs.alignedTo(layout), decoded.configs.alignedTo(layout))
        assertEquals("KMH", decoded.configs.configAt(0, 0).titleOverride)
        assertFalse(decoded.configs.configAt(1, 0).visible)
    }

    @Test
    fun boardDocumentRejectsMalformedInputAndUnknownWidgets() {
        assertNull(BoardDocument.import(null))
        assertNull(BoardDocument.import(""))
        assertNull(BoardDocument.import("not json"))
        assertNull(BoardDocument.import("{\"version\":2,\"boards\":[]}"))
        assertNull(BoardDocument.import("{\"version\":99,\"boards\":[{\"widgets\":[{\"type\":\"speed\"}]}]}"))
        val decoded = BoardDocument.import(
            "{\"version\":2,\"boards\":[{\"widgets\":[{\"type\":\"speed\"},{\"type\":\"unknown\"}]}]}"
        )
        assertNotNull(decoded)
        assertEquals(listOf(BoardWidgetType.SPEED), decoded!!.layout.boards[0])
    }

    @Test
    fun boardDocumentEnforcesLimits() {
        val manyWidgets = (0..(BoardLimits.MAX_WIDGETS_PER_BOARD + 5)).joinToString(",") {
            "{\"type\":\"speed\"}"
        }
        val manyBoards = (0..(BoardLimits.MAX_BOARDS + 3)).joinToString(",") {
            "{\"widgets\":[$manyWidgets]}"
        }
        val decoded = BoardDocument.import("{\"version\":2,\"boards\":[$manyBoards]}")
        assertNotNull(decoded)
        assertEquals(BoardLimits.MAX_BOARDS, decoded!!.layout.boards.size)
        decoded.layout.boards.forEach { assertEquals(BoardLimits.MAX_WIDGETS_PER_BOARD, it.size) }
    }

    @Test
    fun legacyLayoutMigratesToDefaultWidgetConfigs() {
        val legacy = BoardLayout.fromJson("speed|clock|voltage|map")
        val configs = BoardWidgetConfigs.EMPTY.alignedTo(legacy)
        assertEquals(4, configs.board(0).size)
        assertEquals(0, configs.configAt(0, 0).decimals) // speed
        assertEquals(1, configs.configAt(0, 2).decimals) // voltage keeps one decimal
        assertEquals(2, configs.configAt(0, 3).span)     // map spans the full row
        assertEquals(WidgetConfig.DEFAULT_REFRESH_MS, configs.configAt(0, 0).refreshMs)
    }

    @Test
    fun widgetDefaultConfigsMatchRendererExpectations() {
        assertEquals(2, BoardWidgetType.MAP.defaultWidgetConfig().span)
        assertEquals(1, BoardWidgetType.VOLTAGE.defaultWidgetConfig().decimals)
        assertEquals(0, BoardWidgetType.SPEED.defaultWidgetConfig().decimals)
    }

    @Test
    fun themePresetsResolveToKnownWidgets() {
        val presets = listOf(
            listOf("speed", "clock", "fuel", "voltage", "map", "connection"),
            listOf("rpm", "speed", "rpm_graph", "voltage", "gear"),
            listOf("trip_distance", "trip_duration", "connection")
        )
        presets.forEach { preset ->
            val layout = BoardLayout.fromPreset(listOf(preset))
            assertNotNull("preset should yield a layout", layout)
            assertEquals(preset.map { it }, layout!!.boards[0].map { it.id })
        }
        assertNull(BoardLayout.fromPreset(listOf(listOf("nope"))))
    }

    @Test
    fun configUpdatesStayAlignedWithWidgets() {
        var configs = BoardWidgetConfigs(listOf(listOf(WidgetConfig(), WidgetConfig())))
        configs = configs.withAdded(0, WidgetConfig(titleOverride = "Third"))
        assertEquals(3, configs.board(0).size)
        configs = configs.withMoved(0, 2, 0)
        assertEquals("Third", configs.configAt(0, 0).titleOverride)
        configs = configs.withRemoved(0, 0)
        assertEquals(2, configs.board(0).size)
        configs = configs.withInserted(0, 0, WidgetConfig(titleOverride = "Inserted"))
        assertEquals("Inserted", configs.configAt(0, 0).titleOverride)
        // Updates ignore out-of-range indices instead of throwing.
        assertEquals(configs, configs.withRemoved(5, 0))
        assertEquals(configs, configs.withUpdated(0, 99, WidgetConfig()))
    }
}
