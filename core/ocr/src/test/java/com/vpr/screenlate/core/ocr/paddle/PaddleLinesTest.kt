package com.vpr.screenlate.core.ocr.paddle

import com.google.common.truth.Truth.assertThat
import com.vpr.screenlate.core.common.geometry.Box
import com.vpr.screenlate.core.ocr.OcrLine
import com.vpr.screenlate.core.ocr.OcrWord
import org.junit.Test

class PaddleLinesTest {
    private val bounds = Box(0f, 0f, 1280f, 2856f)

    @Test
    fun `overlapping and nearby fragments join before recognition without dropping a small letter`() {
        val lines = listOf(line(10f, 10f, 100f, 30f), line(95f, 11f, 150f, 29f), line(154f, 14f, 164f, 26f))
        val joined = PaddleLines.combine(lines, bounds)
        assertThat(joined).hasSize(1)
        assertThat(joined.single().box).isEqualTo(Box(10f, 10f, 164f, 30f))
        val darkPage = listOf(line(44f, 1221f, 508f, 1267f), line(529f, 1228f, 551f, 1259f),
            line(569f, 1215f, 752f, 1265f))
        assertThat(PaddleLines.combine(darkPage, bounds)).hasSize(1)
    }

    @Test
    fun `contained islands and duplicate boxes are not recognized twice`() {
        val large = line(10f, 10f, 200f, 40f)
        assertThat(PaddleLines.combine(listOf(large, line(80f, 18f, 90f, 28f), large), bounds)).containsExactly(large)
        assertThat(PaddleLines.combine(emptyList(), bounds)).isEmpty()
    }

    @Test
    fun `parallel rows and columns with a gutter stay separate`() {
        val lines = listOf(line(10f, 10f, 100f, 30f), line(115f, 10f, 210f, 30f), line(10f, 40f, 100f, 60f))
        assertThat(PaddleLines.combine(lines, bounds)).hasSize(3)
    }

    @Test
    fun `vertical fragments keep their existing crops and the next column`() {
        val lines = listOf(line(100f, 10f, 120f, 100f), line(100f, 95f, 120f, 180f), line(70f, 10f, 90f, 180f))
        val joined = PaddleLines.combine(lines, bounds)
        assertThat(joined).containsExactlyElementsIn(lines)
    }

    @Test
    fun `slanted fragments keep their baseline`() {
        val direction = OrientedBox(50f, 20f, 0.9848077f, 0.17364818f, 100f, 20f)
        val continuation = direction.copy(x = direction.x + direction.alongX * 95,
            y = direction.y + direction.alongY * 95, length = 80f)
        val joined = PaddleLines.combine(listOf(DetectedLine(direction, direction.bounds(), 0.9f),
            DetectedLine(continuation, continuation.bounds(), 0.8f)), bounds).single()
        assertThat(joined.area.alongX).isWithin(0.00001f).of(direction.alongX)
        assertThat(joined.area.length).isWithin(0.001f).of(175f)
        assertThat(joined.area.thickness).isWithin(0.001f).of(20f)
    }

    @Test
    fun `an icon above the label baseline stays a separate crop`() {
        val lines = listOf(line(197f, 543f, 404f, 594f), line(97f, 544f, 169f, 618f))
        assertThat(PaddleLines.combine(lines, Box(0f, 0f, 1280f, 2856f))).hasSize(2)
        val aligned = listOf(line(10f, 10f, 40f, 40f), line(45f, 15f, 150f, 35f))
        assertThat(PaddleLines.combine(aligned, bounds)).hasSize(2)
    }

    @Test
    fun `the measured Russian page has one crop per printed line`() {
        val combined = PaddleLines.combine(russianPage(), Box(0f, 0f, 1280f, 2856f))
        // Eighteen printed content rows and one status-bar clock; no recognized text is stored here.
        assertThat(combined).hasSize(19)
    }

    @Test
    fun `the measured Russian rows form six content paragraphs and a clock`() {
        val rows = PaddleLines.combine(russianPage(), Box(0f, 0f, 1280f, 2856f)).map {
            OcrLine(listOf(OcrWord("row", "", it.box)), it.box, vertical = false)
        }
        assertThat(PaddleParagraphs.from(rows).map { it.lines.size }).containsExactly(1, 3, 3, 4, 3, 3, 2).inOrder()
    }

    private fun line(left: Float, top: Float, right: Float, bottom: Float) = DetectedLine(
        OrientedBox(left, top, 1f, 0f, right - left, bottom - top), Box(left, top, right, bottom), 0.9f,
    )

    /** Bounding boxes from the generated ru-page-light comparison with the v5 Cyrillic recognizer. */
    private fun russianPage() = listOf(
        line(43f, 52f, 149f, 105f),
        line(137f, 54f, 191f, 103f),
        line(139f, 78f, 148f, 87f),
        line(41f, 207f, 598f, 285f),
        line(555f, 212f, 1116f, 284f),
        line(45f, 296f, 514f, 362f),
        line(511f, 298f, 835f, 359f),
        line(493f, 320f, 503f, 338f),
        line(46f, 373f, 454f, 435f),
        line(41f, 476f, 1207f, 542f),
        line(42f, 552f, 1015f, 612f),
        line(44f, 628f, 693f, 684f),
        line(43f, 734f, 969f, 801f),
        line(45f, 819f, 843f, 882f),
        line(832f, 819f, 1081f, 879f),
        line(45f, 902f, 1191f, 963f),
        line(45f, 986f, 443f, 1044f),
        line(41f, 1084f, 700f, 1147f),
        line(721f, 1096f, 1088f, 1146f),
        line(697f, 1103f, 736f, 1138f),
        line(684f, 1108f, 700f, 1127f),
        line(728f, 1112f, 737f, 1125f),
        line(685f, 1153f, 1039f, 1209f),
        line(48f, 1156f, 207f, 1203f),
        line(360f, 1156f, 534f, 1207f),
        line(218f, 1157f, 382f, 1204f),
        line(548f, 1159f, 695f, 1206f),
        line(677f, 1171f, 704f, 1194f),
        line(564f, 1215f, 752f, 1265f),
        line(45f, 1221f, 261f, 1269f),
        line(266f, 1222f, 514f, 1270f),
        line(525f, 1227f, 558f, 1261f),
        line(499f, 1237f, 507f, 1245f),
        line(43f, 1319f, 1208f, 1376f),
        line(43f, 1391f, 1134f, 1448f),
        line(42f, 1459f, 728f, 1525f),
        line(45f, 1562f, 1230f, 1612f),
        line(45f, 1616f, 1167f, 1660f),
    )
}
