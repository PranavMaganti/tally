package me.vanpra.tally.course

import kotlinx.serialization.Serializable

/**
 * A course as the app stores it: plain coordinates, independent of where the data came from
 * (OpenStreetMap today, a licensed provider later).
 */
@Serializable
data class Course(
    val id: String,
    val name: String,
    val holes: List<Hole>,
    val source: CourseSource,
    /** Every mapped surface on the course, for drawing the map. Distances use the per-hole data instead. */
    val mapFeatures: List<MapFeature> = emptyList(),
)

@Serializable
data class MapFeature(val type: SurfaceType, val outline: List<LatLng>, val form: Form = Form.AREA)

/** Areas are closed shapes, lines are open (paths, rows of trees), points are single objects (a tree). */
@Serializable
enum class Form { AREA, LINE, POINT }

@Serializable
enum class SurfaceType { FAIRWAY, GREEN, TEE, BUNKER, WATER, ROUGH, WOOD, TREE, BUILDING, PATH }

@Serializable
enum class CourseSource { OPEN_STREET_MAP }

@Serializable
data class Hole(
    val number: Int,
    val par: Int?,
    /** The mapped route of the hole, from the tee to the middle of the green. */
    val line: List<LatLng>,
    val green: List<LatLng>?,
    val tees: List<List<LatLng>> = emptyList(),
    val hazards: List<Hazard> = emptyList(),
)

@Serializable
data class Hazard(val type: HazardType, val outline: List<LatLng>)

@Serializable
enum class HazardType { BUNKER, WATER, LATERAL_WATER }
