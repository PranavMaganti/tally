package me.vanpra.tally.course

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Imports the real Wanstead OSM data (snapshot from Nov 2026) and checks it against the numbers
 * the browser prototype produced from the same data.
 */
class WansteadImportTest {
    private val course = OsmCourseParser.parse(
        overpassJson = requireNotNull(javaClass.classLoader.getResource("wanstead-osm.json")).readText(),
        courseId = "osm-relation-3883387",
    )

    private fun yards(m: Double) = (m * METRES_TO_YARDS).roundToInt()

    @Test
    fun allHolesWithParsAndGreens() {
        assertEquals("Wanstead Golf Club", course.name)
        assertEquals((1..18).toList(), course.holes.map { it.number })
        assertEquals(69, course.holes.sumOf { it.par ?: 0 })
        course.holes.forEach { assertNotNull(it.green, "hole ${it.number} has no green") }
    }

    @Test
    fun holeLengthsMatchPrototype() {
        val lengths = course.holes.map { yards(HoleDistances(it).lengthMetres) }
        assertEquals(344, lengths[0])
        assertEquals(459, lengths[6])
        assertEquals(152, lengths[12])
        assertTrue(lengths.sum() in 5785..5795, "total ${lengths.sum()}")
    }

    @Test
    fun seventhHoleFromTheDriveMatchesPrototype() {
        val d = HoleDistances(course.holes[6])
        val player = d.pointAlongHole(212 / METRES_TO_YARDS)
        val g = assertNotNull(d.green(player))
        assertEquals(230, yards(g.front))
        assertEquals(245, yards(g.middle))
        assertEquals(259, yards(g.back))
    }

    @Test
    fun seventhHoleHasBunkersAhead() {
        val d = HoleDistances(course.holes[6])
        val ahead = d.hazardsAhead(d.pointAlongHole(212 / METRES_TO_YARDS))
        assertTrue(ahead.isNotEmpty())
        assertTrue(ahead.all { it.hazard.type == HazardType.BUNKER && it.reach <= it.carry })
    }
}
