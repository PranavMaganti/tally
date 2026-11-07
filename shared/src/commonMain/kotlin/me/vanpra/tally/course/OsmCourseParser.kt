package me.vanpra.tally.course

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Builds a [Course] from an Overpass API response made with `out geom;`
 * containing the course's `golf=*` features (and optionally the `leisure=golf_course` boundary).
 *
 * OSM doesn't link greens, tees or hazards to holes, so they're assigned by geometry:
 * the green that contains (or is nearest) the end of each `golf=hole` line, tees near its start,
 * and hazards whose middle lies close to the line.
 */
object OsmCourseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(overpassJson: String, courseId: String, fallbackName: String = "Golf course"): Course {
        val elements = json.parseToJsonElement(overpassJson).jsonObject["elements"]?.jsonArray ?: JsonArray(emptyList())
        val features = elements.mapNotNull { (it as? JsonObject)?.let(::toFeature) }

        val name = features.firstOrNull { it.tags["leisure"] == "golf_course" }?.tags?.get("name") ?: fallbackName
        val holeLines = features.filter { it.golf == "hole" && it.outlines.isNotEmpty() && it.tags["ref"]?.toIntOrNull() != null }
        val greens = features.filter { it.golf == "green" }.flatMap { it.outlines }
        val tees = features.filter { it.golf == "tee" }.flatMap { it.outlines }
        val hazards = features.flatMap { f ->
            val type = when (f.golf) {
                "bunker" -> HazardType.BUNKER
                "water_hazard" -> HazardType.WATER
                "lateral_water_hazard" -> HazardType.LATERAL_WATER
                else -> null
            } ?: return@flatMap emptyList()
            f.outlines.map { Hazard(type, it) }
        }

        val holes = holeLines.map { f ->
            val line = f.outlines.first()
            val projection = LocalProjection(line.first())
            val linePts = line.map(projection::toPoint)
            val start = linePts.first()
            val end = linePts.last()

            val green = greens
                .map { it to Polygon.of(it, projection) }
                .let { candidates ->
                    candidates.firstOrNull { (_, poly) -> end in poly }
                        ?: candidates.minByOrNull { (_, poly) -> poly.centroid.distanceTo(end) }
                            ?.takeIf { (_, poly) -> poly.centroid.distanceTo(end) < MAX_GREEN_OFFSET_M }
                }?.first

            Hole(
                number = f.tags.getValue("ref").toInt(),
                par = f.tags["par"]?.toIntOrNull(),
                line = line,
                green = green,
                tees = tees.filter { Polygon.of(it, projection).nearestDistance(start) < MAX_TEE_OFFSET_M },
                hazards = hazards.filter { polylineDistance(Polygon.of(it.outline, projection).centroid, linePts) < HAZARD_CORRIDOR_M },
            )
        }.sortedBy { it.number }

        val mapFeatures = features.flatMap { f -> f.outlines.mapNotNull { outline -> mapFeature(f.tags, outline) } }

        return Course(id = courseId, name = name, holes = holes, source = CourseSource.OPEN_STREET_MAP, mapFeatures = mapFeatures)
    }

    /** What to draw for a feature, or null if it isn't something the map shows. */
    private fun mapFeature(tags: Map<String, String>, outline: List<LatLng>): MapFeature? {
        val point = outline.size == 1
        val closed = !point && outline.first() == outline.last()
        fun area(type: SurfaceType) = if (closed) MapFeature(type, outline) else null
        return when {
            tags["golf"] == "fairway" -> area(SurfaceType.FAIRWAY)
            tags["golf"] == "green" -> area(SurfaceType.GREEN)
            tags["golf"] == "tee" -> area(SurfaceType.TEE)
            tags["golf"] == "bunker" -> area(SurfaceType.BUNKER)
            tags["golf"] == "rough" -> area(SurfaceType.ROUGH)
            tags["golf"] in setOf("water_hazard", "lateral_water_hazard") || tags["natural"] in setOf("water", "wetland") -> area(SurfaceType.WATER)
            tags["natural"] in setOf("wood", "scrub") || tags["landuse"] == "forest" -> area(SurfaceType.WOOD)
            tags["natural"] == "tree" && point -> MapFeature(SurfaceType.TREE, outline, Form.POINT)
            tags["natural"] == "tree_row" -> MapFeature(SurfaceType.TREE, outline, Form.LINE)
            tags.containsKey("building") || tags["amenity"] == "shelter" -> if (point) MapFeature(SurfaceType.BUILDING, outline, Form.POINT) else area(SurfaceType.BUILDING)
            tags["golf"] in setOf("path", "cartpath") || tags["highway"] in setOf("path", "footway", "track", "bridleway") ->
                if (point) null else MapFeature(SurfaceType.PATH, outline, Form.LINE)
            else -> null
        }
    }

    private class OsmFeature(val tags: Map<String, String>, val outlines: List<List<LatLng>>) {
        val golf get() = tags["golf"]
    }

    private fun toFeature(e: JsonObject): OsmFeature? {
        val tags = (e["tags"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content } ?: return null
        val outlines = when (e["type"]?.jsonPrimitive?.content) {
            "node" -> listOf(listOf(LatLng(e.getValue("lat").jsonPrimitive.double, e.getValue("lon").jsonPrimitive.double)))
            "way" -> listOfNotNull(geometry(e))
            "relation" -> e["members"]?.jsonArray.orEmpty()
                .map { it.jsonObject }
                .filter { it["role"]?.jsonPrimitive?.content == "outer" }
                .mapNotNull(::geometry)
            else -> emptyList()
        }
        return OsmFeature(tags, outlines)
    }

    private fun geometry(o: JsonObject): List<LatLng>? = o["geometry"]?.jsonArray
        ?.map { p -> p.jsonObject.let { LatLng(it.getValue("lat").jsonPrimitive.double, it.getValue("lon").jsonPrimitive.double) } }
        ?.takeIf { it.isNotEmpty() }

    private const val MAX_GREEN_OFFSET_M = 60.0
    private const val MAX_TEE_OFFSET_M = 40.0
    private const val HAZARD_CORRIDOR_M = 45.0
}
