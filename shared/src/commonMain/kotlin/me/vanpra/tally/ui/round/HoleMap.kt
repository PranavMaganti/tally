package me.vanpra.tally.ui.round

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import me.vanpra.tally.course.Course
import me.vanpra.tally.course.Form
import me.vanpra.tally.course.HazardDistance
import me.vanpra.tally.course.HoleDistances
import me.vanpra.tally.course.LatLng
import me.vanpra.tally.course.LocalProjection
import me.vanpra.tally.course.MapFeature
import me.vanpra.tally.course.METRES_TO_YARDS
import me.vanpra.tally.course.Point
import me.vanpra.tally.course.Polygon
import me.vanpra.tally.course.Side
import me.vanpra.tally.course.SurfaceType
import me.vanpra.tally.course.distanceMetres
import me.vanpra.tally.course.polylineDistance
import me.vanpra.tally.round.TrackedShot
import me.vanpra.tally.ui.theme.Tally
import me.vanpra.tally.ui.theme.TallyColors
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Width of the lit lane drawn along the hole, in metres. */
private const val LANE_WIDTH_M = 62f

/**
 * Maps between coordinates and screen pixels for one hole, turned so the tee is at the bottom
 * and the green at the top. The part of the hole still to play (from [anchor] to just past the green)
 * is fitted between [topInset] and [bottomInset] pixels, while the map itself draws edge to edge.
 */
internal class HoleFrame(distances: HoleDistances, anchor: LatLng, val size: IntSize, topInset: Float, bottomInset: Float) {
    private val projection = LocalProjection(distances.hole.line.first())
    private val greenMiddle = distances.greenMiddle ?: distances.hole.line.last()
    private val cosR: Double
    private val sinR: Double
    val pixelsPerMetre: Double
    private val centre: Point
    private val screenCx = size.width / 2.0
    private val screenCy: Double

    init {
        val g = projection.toPoint(greenMiddle)
        val rotation = PI / 2 - atan2(g.y, g.x)
        cosR = cos(rotation); sinR = sin(rotation)
        val a = projection.toPoint(anchor)
        // Include the bend of a dogleg, but only the part of the hole still in front of the anchor.
        val ahead = distances.hole.line.map(projection::toPoint).filter { it.distanceTo(g) < a.distanceTo(g) }
        val pts = (distances.hole.green.orEmpty().map(projection::toPoint) + ahead + a + g).map(::rotate)
        val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }
        val top = pts.maxOf { it.y } + 8.0
        val bottom = pts.minOf { it.y } - 8.0
        val areaH = (size.height - topInset - bottomInset).coerceAtLeast(1f).toDouble()
        var w = max(maxX - minX + 60.0, 110.0)
        var h = top - bottom
        val aspect = size.width / areaH
        if (w / h < aspect) w = h * aspect else h = w / aspect
        pixelsPerMetre = size.width / w
        centre = Point((minX + maxX) / 2, (top + bottom) / 2)
        screenCy = topInset + areaH / 2
    }

    private fun rotate(p: Point) = Point(p.x * cosR - p.y * sinR, p.x * sinR + p.y * cosR)
    private fun unrotate(p: Point) = Point(p.x * cosR + p.y * sinR, -p.x * sinR + p.y * cosR)

    fun toScreen(p: LatLng): Offset {
        val r = rotate(projection.toPoint(p))
        return Offset((screenCx + (r.x - centre.x) * pixelsPerMetre).toFloat(), (screenCy - (r.y - centre.y) * pixelsPerMetre).toFloat())
    }

    fun toLatLng(o: Offset): LatLng {
        val r = Point(centre.x + (o.x - screenCx) / pixelsPerMetre, centre.y - (o.y - screenCy) / pixelsPerMetre)
        return projection.toLatLng(unrotate(r))
    }

    fun isVisible(o: Offset, margin: Float) = o.x > -margin && o.y > -margin && o.x < size.width + margin && o.y < size.height + margin
}

/** How a mapped feature relates to the hole being played. */
private enum class Layer { BACKGROUND, HOLE, NEARBY }

private class Shape(val feature: MapFeature, val layer: Layer, val path: Path?, val points: List<Offset>, val isThisGreen: Boolean)

private val CONTEXT_ORDER = listOf(SurfaceType.WATER, SurfaceType.WOOD, SurfaceType.PATH, SurfaceType.BUILDING, SurfaceType.TREE)
private val GOLF_ORDER = listOf(SurfaceType.ROUGH, SurfaceType.FAIRWAY, SurfaceType.TEE, SurfaceType.GREEN, SurfaceType.BUNKER)

/**
 * The drawn hole map, in layers: the rest of the course (washed back), a lit lane along this hole,
 * this hole's surfaces, then trees, woods, water, paths and buildings near the hole, then distances.
 * Shapes are turned into paths once per framing and reused, so a GPS fix or a dragged target only
 * redraws the overlay.
 */
@Composable
internal fun HoleMap(
    course: Course,
    distances: HoleDistances,
    anchor: LatLng,
    player: LatLng,
    target: LatLng?,
    hazards: List<HazardDistance>,
    /** Shots you've marked on this hole, drawn as your ball's path. */
    shots: List<TrackedShot>,
    canMovePlayer: Boolean,
    onTargetChange: (LatLng) -> Unit,
    onPlayerMove: (LatLng) -> Unit,
    onPlayerMoveEnd: () -> Unit,
    topInset: Float,
    bottomInset: Float,
    modifier: Modifier = Modifier,
) {
    val colors = Tally.colors
    val numberStyle = Tally.type.numberMedium
    val measurer = rememberTextMeasurer()
    var size by remember { mutableStateOf(IntSize.Zero) }
    val frame = remember(distances, anchor, size, topInset, bottomInset) {
        if (size.width == 0) null else HoleFrame(distances, anchor, size, topInset, bottomInset)
    }

    val classifier = remember(distances) { HoleClassifier(distances) }
    val layers = remember(course, classifier) { course.mapFeatures.map { it to classifier.layerOf(it) } }
    val shapes = remember(frame, layers) {
        frame ?: return@remember emptyList()
        layers.mapNotNull { (f, layer) ->
            val pts = f.outline.map(frame::toScreen)
            if (pts.none { frame.isVisible(it, 300f) }) return@mapNotNull null
            val path = if (f.form == Form.POINT) null else Path().apply {
                moveTo(pts[0].x, pts[0].y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
                if (f.form == Form.AREA) close()
            }
            Shape(f, layer, path, pts, f.type == SurfaceType.GREEN && f.outline == distances.hole.green)
        }
    }
    // Nearby trees, woods and paths are drawn faded with the rest of the course, then again at full strength
    // but clipped to the lane, so only what's actually in play stands out.
    val background = remember(shapes) { shapes.filter { it.layer != Layer.HOLE } }
    val holeShapes = remember(shapes) { shapes.filter { it.layer == Layer.HOLE }.sortedBy { GOLF_ORDER.indexOf(it.feature.type) } }
    val nearby = remember(shapes) { shapes.filter { it.layer == Layer.NEARBY }.sortedBy { CONTEXT_ORDER.indexOf(it.feature.type) } }
    val holeScreenPoints = remember(frame) { frame?.let { fr -> distances.hole.line.map(fr::toScreen) } }
    val holeLine = remember(holeScreenPoints) {
        holeScreenPoints?.let { pts -> Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } } }
    }

    val laneClip = remember(holeScreenPoints, frame) {
        val pts = holeScreenPoints ?: return@remember null
        val half = (LANE_WIDTH_M / 2 * (frame?.pixelsPerMetre ?: return@remember null)).toFloat()
        lanePath(pts, half)
    }

    val currentFrame by rememberUpdatedState(frame)
    val currentPlayer by rememberUpdatedState(player)
    val movePlayer by rememberUpdatedState(canMovePlayer)
    val setTarget by rememberUpdatedState(onTargetChange)
    val setPlayer by rememberUpdatedState(onPlayerMove)
    val endPlayer by rememberUpdatedState(onPlayerMoveEnd)

    Canvas(
        modifier
            .clipToBounds()
            .onSizeChanged { size = it }
            .pointerInput(Unit) { detectTapGestures { o -> currentFrame?.let { setTarget(it.toLatLng(o)) } } }
            .pointerInput(Unit) {
                var draggingPlayer = false
                detectDragGestures(
                    onDragStart = { o ->
                        val f = currentFrame ?: return@detectDragGestures
                        draggingPlayer = movePlayer && (f.toScreen(currentPlayer) - o).getDistance() < 48.dp.toPx()
                        if (!draggingPlayer) setTarget(f.toLatLng(o))
                    },
                    onDragEnd = { if (draggingPlayer) endPlayer() },
                    onDrag = { change, _ ->
                        val f = currentFrame ?: return@detectDragGestures
                        if (draggingPlayer) setPlayer(f.toLatLng(change.position)) else setTarget(f.toLatLng(change.position))
                    },
                )
            },
    ) {
        val f = frame ?: return@Canvas
        val ppm = f.pixelsPerMetre.toFloat()

        // 1. The rest of the course, then a wash that pushes it back.
        drawRect(colors.mapRough)
        background.forEach { drawFeature(it, colors, ppm) }
        drawRect(colors.bg.copy(alpha = 0.62f))

        // 2. A lit lane along this hole, soft at the edges.
        holeLine?.let {
            drawPath(it, colors.mapRough.copy(alpha = 0.5f), style = Stroke(LANE_WIDTH_M * 1.35f * ppm, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(it, colors.mapRough, style = Stroke(LANE_WIDTH_M * ppm, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // 3. This hole's surfaces at full strength, then what's around it.
        holeShapes.forEach { drawFeature(it, colors, ppm, emphasise = true) }
        laneClip?.let { lane -> clipPath(lane) { nearby.forEach { drawFeature(it, colors, ppm) } } }

        val me = f.toScreen(player)
        val greenLL = distances.greenMiddle ?: distances.hole.line.last()
        val green = f.toScreen(greenLL)
        val toGreen = distanceMetres(player, greenLL)
        val placed = mutableListOf<Rect>()

        // 4. Yardage-book arcs across the lane, from the middle of the green, for distances in front of you.
        val half = LANE_WIDTH_M / 2 * ppm
        listOf(100, 150, 200, 250).forEach { yds ->
            val m = yds / METRES_TO_YARDS
            if (m > toGreen - 15) return@forEach
            val r = (m * ppm).toFloat()
            val onLine = pointAtDistanceFrom(green, holeScreenPoints ?: return@forEach, r) ?: return@forEach
            val angle = atan2(onLine.y - green.y, onLine.x - green.x) * 180 / PI
            val span = asin((half / r).coerceAtMost(1f)) * 180 / PI
            drawArc(
                colors.mapInk.copy(alpha = 0.5f), (angle - span).toFloat(), (2 * span).toFloat(), useCenter = false,
                topLeft = green - Offset(r, r), size = Size(2 * r, 2 * r), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round),
            )
            val endRad = (angle - span) * PI / 180
            val end = green + Offset((r * cos(endRad)).toFloat(), (r * sin(endRad)).toFloat())
            val layout = measurer.measure(yds.toString(), numberStyle.copy(color = colors.mapInk.copy(alpha = 0.7f), fontSize = numberStyle.fontSize * 0.85f))
            val topLeft = end + Offset(6.dp.toPx(), -layout.size.height / 2f)
            drawText(layout, topLeft = topLeft)
            placed += Rect(topLeft, Size(layout.size.width.toFloat(), layout.size.height.toFloat()))
        }

        // 5. Line to the target or green, with distance labels.
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
        if (target != null) {
            val t = f.toScreen(target)
            drawLine(colors.flag, me, t, 2.5.dp.toPx(), StrokeCap.Round)
            drawLine(colors.flag, t, green, 2.dp.toPx(), StrokeCap.Round, pathEffect = dash)
            placed += pill(measurer, numberStyle, (me + t) / 2f, yards(distanceMetres(player, target)), colors.surface, colors.ink)
            placed += pill(measurer, numberStyle, (t + green) / 2f, yards(distanceMetres(target, greenLL)), colors.surface, colors.ink)
            drawCircle(colors.flag.copy(alpha = 0.2f), 18.dp.toPx(), t)
            drawCircle(colors.flag, 18.dp.toPx(), t, style = Stroke(2.5.dp.toPx()))
            drawCircle(colors.flag, 3.dp.toPx(), t)
        } else {
            drawLine(colors.mapInk.copy(alpha = 0.55f), me, green, 1.5.dp.toPx(), pathEffect = dash)
        }

        // 6. Each hazard ahead gets "reach–carry" beside it, on the side away from the line. Labels that would overlap are skipped.
        val hazardStyle = numberStyle.copy(fontSize = numberStyle.fontSize * 0.9f)
        hazards.forEach { h ->
            val pts = h.hazard.outline.map(f::toScreen)
            val minX = pts.minOf { it.x }; val maxX = pts.maxOf { it.x }
            val midY = (pts.minOf { it.y } + pts.maxOf { it.y }) / 2
            val text = "${yards(h.reach)}–${yards(h.carry)}"
            val layout = measurer.measure(text, hazardStyle.copy(color = colors.ink))
            val w = layout.size.width + 12.dp.toPx(); val hgt = layout.size.height + 4.dp.toPx()
            val gap = 5.dp.toPx()
            val left = if (h.side == Side.LEFT) minX - gap - w else maxX + gap
            val rect = Rect(Offset(left, midY - hgt / 2), Size(w, hgt))
            if (placed.any { it.overlaps(rect) } || !f.isVisible(rect.center, 0f)) return@forEach
            drawRoundRect(colors.surface.copy(alpha = 0.94f), rect.topLeft, rect.size, CornerRadius(hgt / 2))
            drawRoundRect(colors.mapSandEdge, rect.topLeft, rect.size, CornerRadius(hgt / 2), style = Stroke(1.dp.toPx()))
            drawText(layout, topLeft = rect.topLeft + Offset(6.dp.toPx(), 2.dp.toPx()))
            placed += rect
        }

        // 7. Your ball's path so far: each shot a solid line to a numbered dot, with how far it went.
        val worked = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
        shots.forEach { s ->
            val a = f.toScreen(s.from); val b = f.toScreen(s.to)
            // A shot worked out from the score, not marked: dashed, since only its start is known exactly.
            drawLine(Color.White.copy(alpha = 0.9f), a, b, 2.5.dp.toPx(), StrokeCap.Round, pathEffect = if (s.inferred) worked else null)
        }
        shots.forEach { s ->
            val a = f.toScreen(s.from); val b = f.toScreen(s.to)
            val label = (if (s.inferred) "≈" else "") + yards(s.metres) + (s.club?.let { " · $it" } ?: "")
            val layout = measurer.measure(label, numberStyle.copy(color = colors.ink, fontSize = numberStyle.fontSize * 0.8f))
            val w = layout.size.width + 12.dp.toPx(); val h = layout.size.height + 4.dp.toPx()
            val mid = (a + b) / 2f
            val rect = Rect(Offset(mid.x + 8.dp.toPx(), mid.y - h / 2), Size(w, h))
            if (placed.none { it.overlaps(rect) } && f.isVisible(rect.center, 0f)) {
                drawRoundRect(colors.surface.copy(alpha = 0.94f), rect.topLeft, rect.size, CornerRadius(h / 2))
                drawText(layout, topLeft = rect.topLeft + Offset(6.dp.toPx(), 2.dp.toPx()))
                placed += rect
            }
            if (s.inferred) return@forEach
            drawCircle(Color.Black.copy(alpha = 0.25f), 9.dp.toPx(), b + Offset(1f, 1.5f))
            drawCircle(Color.White, 9.dp.toPx(), b)
            // Found by Tally rather than marked: a ring in the accent colour says so.
            if (s.auto) drawCircle(colors.accent, 9.dp.toPx(), b, style = Stroke(2.dp.toPx()))
            val n = measurer.measure(s.number.toString(), numberStyle.copy(color = colors.ink, fontSize = numberStyle.fontSize * 0.7f))
            drawText(n, topLeft = b - Offset(n.size.width / 2f, n.size.height / 2f))
        }

        flag(green, colors.flag)
        drawCircle(colors.blue.copy(alpha = 0.25f), 16.dp.toPx(), me)
        drawCircle(Color.White, 8.dp.toPx(), me)
        drawCircle(colors.blue, 6.dp.toPx(), me)
    }
}

/** Decides which mapped features belong to the hole being played, and which are close enough to matter. */
private class HoleClassifier(distances: HoleDistances) {
    private val hole = distances.hole
    private val projection = LocalProjection(hole.line.first())
    private val line = hole.line.map(projection::toPoint)
    private val hazardOutlines = hole.hazards.map { it.outline }.toSet()
    private val nearbyMetres = LANE_WIDTH_M / 2 + 15.0

    fun layerOf(f: MapFeature): Layer = when (f.type) {
        SurfaceType.GREEN -> if (f.outline == hole.green) Layer.HOLE else Layer.BACKGROUND
        SurfaceType.TEE -> if (f.outline in hole.tees) Layer.HOLE else Layer.BACKGROUND
        SurfaceType.BUNKER -> if (f.outline in hazardOutlines) Layer.HOLE else Layer.BACKGROUND
        SurfaceType.FAIRWAY, SurfaceType.ROUGH -> {
            val poly = Polygon.of(f.outline, projection)
            if (line.any { it in poly } || polylineDistance(poly.centroid, line) < 25.0) Layer.HOLE else Layer.BACKGROUND
        }
        else -> if (isNear(f)) Layer.NEARBY else Layer.BACKGROUND
    }

    private fun isNear(f: MapFeature): Boolean {
        val pts = f.outline.map(projection::toPoint)
        if (pts.any { polylineDistance(it, line) < nearbyMetres }) return true
        return f.form == Form.AREA && pts.size >= 3 && Polygon(pts).let { poly -> line.any { it in poly } }
    }
}

private fun DrawScope.drawFeature(s: Shape, c: TallyColors, ppm: Float, emphasise: Boolean = false) {
    val type = s.feature.type
    when (s.feature.form) {
        Form.POINT -> s.points.firstOrNull()?.let { p ->
            if (type == SurfaceType.TREE) {
                val r = max(4.5f * ppm, 3.dp.toPx())
                drawCircle(c.mapTree, r, p)
                drawCircle(c.mapTreeEdge, r, p, style = Stroke(1.dp.toPx()))
            } else {
                val half = 3.5.dp.toPx()
                drawRect(c.mapBuilding, p - Offset(half, half), Size(half * 2, half * 2))
                drawRect(c.mapBuildingEdge, p - Offset(half, half), Size(half * 2, half * 2), style = Stroke(1.dp.toPx()))
            }
        }
        Form.LINE -> s.path?.let { path ->
            when (type) {
                SurfaceType.TREE -> drawPath(path, c.mapTree, style = Stroke(max(8f * ppm, 5.dp.toPx()), cap = StrokeCap.Round, join = StrokeJoin.Round))
                else -> drawPath(path, c.mapPath, style = Stroke(max(1.5f * ppm, 1.5.dp.toPx()), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Form.AREA -> s.path?.let { path ->
            val fill = when (type) {
                SurfaceType.FAIRWAY -> c.mapFairway
                SurfaceType.GREEN -> c.mapGreen
                SurfaceType.TEE -> c.mapTee
                SurfaceType.BUNKER -> c.mapSand
                SurfaceType.WATER -> c.mapWater
                SurfaceType.ROUGH -> c.mapRough
                SurfaceType.WOOD -> c.mapWood
                SurfaceType.TREE -> c.mapTree
                SurfaceType.BUILDING -> c.mapBuilding
                SurfaceType.PATH -> c.mapPath
            }
            drawPath(path, fill)
            when {
                type == SurfaceType.BUNKER -> drawPath(path, c.mapSandEdge, style = Stroke((if (emphasise) 1.5 else 1.0).dp.toPx()))
                type == SurfaceType.BUILDING -> drawPath(path, c.mapBuildingEdge, style = Stroke(1.dp.toPx()))
                type == SurfaceType.WOOD -> drawPath(path, c.mapTreeEdge.copy(alpha = 0.6f), style = Stroke(1.dp.toPx()))
                emphasise && type == SurfaceType.FAIRWAY -> drawPath(path, c.mapGreenEdge.copy(alpha = 0.4f), style = Stroke(1.dp.toPx()))
                emphasise && type == SurfaceType.TEE -> drawPath(path, c.mapInk.copy(alpha = 0.4f), style = Stroke(1.dp.toPx()))
            }
            if (s.isThisGreen) drawPath(path, c.mapGreenEdge, style = Stroke(2.dp.toPx()))
        }
    }
}

/** The lane as a filled shape: a capsule around each segment of the hole line, merged. */
private fun lanePath(points: List<Offset>, half: Float): Path {
    var lane = Path()
    points.forEach { p -> lane = Path.combine(PathOperation.Union, lane, Path().apply { addOval(Rect(p, half)) }) }
    points.zipWithNext().forEach { (a, b) ->
        val d = b - a
        val len = d.getDistance().takeIf { it > 0f } ?: return@forEach
        val n = Offset(-d.y / len * half, d.x / len * half)
        val quad = Path().apply { moveTo((a + n).x, (a + n).y); lineTo((b + n).x, (b + n).y); lineTo((b - n).x, (b - n).y); lineTo((a - n).x, (a - n).y); close() }
        lane = Path.combine(PathOperation.Union, lane, quad)
    }
    return lane
}

/** Walking back from the green end of [line], the first point exactly [r] pixels from [centre]. */
private fun pointAtDistanceFrom(centre: Offset, line: List<Offset>, r: Float): Offset? {
    for (i in line.lastIndex downTo 1) {
        val a = line[i]; val b = line[i - 1]
        val da = (a - centre).getDistance(); val db = (b - centre).getDistance()
        if ((da - r) * (db - r) <= 0f && da != db) return a + (b - a) * ((r - da) / (db - da))
    }
    return null
}

private fun DrawScope.flag(at: Offset, colour: Color) {
    val pole = 26.dp.toPx()
    drawCircle(Color.Black.copy(alpha = 0.25f), 3.dp.toPx(), at + Offset(1f, 1f))
    drawLine(Color.White, at, at - Offset(0f, pole), 2.dp.toPx(), StrokeCap.Round)
    val top = at - Offset(0f, pole)
    val cloth = Path().apply {
        moveTo(top.x + 1f, top.y)
        lineTo(top.x + 15.dp.toPx(), top.y + 5.dp.toPx())
        lineTo(top.x + 1f, top.y + 10.dp.toPx())
        close()
    }
    drawPath(cloth, colour)
    drawCircle(Color.White, 2.5.dp.toPx(), at)
}

private fun yards(metres: Double) = (metres * METRES_TO_YARDS).roundToInt().toString()

private fun DrawScope.pill(measurer: TextMeasurer, style: TextStyle, at: Offset, text: String, bg: Color, fg: Color): Rect {
    val layout = measurer.measure(text, style.copy(color = fg))
    val padX = 8.dp.toPx(); val padY = 3.dp.toPx()
    val w = layout.size.width + padX * 2; val h = layout.size.height + padY * 2
    val topLeft = Offset(at.x - w / 2, at.y - h / 2)
    drawRoundRect(bg, topLeft, Size(w, h), CornerRadius(h / 2))
    drawText(layout, topLeft = topLeft + Offset(padX, padY))
    return Rect(topLeft, Size(w, h))
}
