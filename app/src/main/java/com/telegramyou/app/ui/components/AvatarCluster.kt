package com.telegramyou.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.telegramyou.app.ui.avatars.avatarShapeIndex
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.CornerRounding
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.telegramyou.app.ui.avatars.avatarShapeIndices
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI

/**
 * One member of a group, as far as the cluster is concerned.
 *
 * [seed] decides both colour and shape, so it has to be the person rather
 * than their position: the same id gives the same clover in every group they
 * are in, and across a reload.
 */
data class ClusterMember(val name: String, val seed: Long, val photoPath: String? = null)

/**
 * The people in a group, overlapping, each in a different shape.
 *
 * This is the shape library doing the job it exists for. Material's argument
 * for it is not decoration but recognition — an outline is a second channel
 * alongside colour, and a group's header is exactly where a person scans for
 * "who is in here" rather than reading a list of names.
 *
 * Which shape belongs to whom is decided in :core, with tests, because the
 * two rules — a person keeps their shape, neighbours never share one — pull
 * against each other and are easy to get subtly wrong.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AvatarCluster(
    members: List<ClusterMember>,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    /**
     * How many to draw. Past a handful the shapes stop being distinguishable
     * and the row runs into the chat's name.
     */
    maxShown: Int = 4
) {
    val shown = members.take(maxShown)
    if (shown.isEmpty()) return

    val shapes = materialShapeSet()
    val indices = avatarShapeIndices(shown.map { it.seed }, shapes.size)

    // A third of a step of overlap: enough to read as a group rather than a
    // row, not so much that an outline is lost behind its neighbour — which
    // would throw away the thing being shown.
    val step = size * 0.68f

    Box(modifier = modifier.size(width = step * (shown.size - 1) + size, height = size)) {
        shown.forEachIndexed { position, member ->
            AvatarBubble(
                title = member.name,
                seed = member.seed,
                size = size,
                shape = shapes[indices[position]],
                photoPath = member.photoPath,
                // Drawn in order, so each avatar laps the one before it. The
                // leftmost ends up furthest back, which is the way a hand of
                // cards is held and reads as depth rather than as a mistake.
                modifier = Modifier.offset(x = step * position)
            )
        }
    }
}

/**
 * The shapes a cluster draws from.
 *
 * Built here rather than taken from `MaterialShapes`, and not by choice:
 * every one of that object's thirty-five shapes is `internal` in
 * material3 1.5.0-alpha28. `javap` shows their lazy accessors, which is what
 * fooled the first attempt at this — the compiler then refused all twelve.
 *
 * And alpha28 is not merely what this project pins — it is the newest
 * material3 there is, checked against Google's Maven rather than assumed.
 * There is no version to wait for.
 *
 * So they are made from `androidx.graphics:graphics-shapes`, which is the
 * library the Material catalogue is itself built on and, unlike the
 * catalogue, a stable 1.0.1. This is Material's shape library, used one
 * level down: what is missing is the names, not the shapes. Regular polygons with a vertex count and a
 * corner rounding cover what a cluster needs: the difference the eye uses is
 * how many sides and how soft they are, not whether a shape is precisely
 * Material's "puffy".
 *
 * `normalized()` matters. A polygon is built around its own radius, and
 * without it the shapes come out at different sizes from each other, which
 * reads as a layout bug rather than as variety.
 *
 * Circle is first and deliberately a plain [CircleShape]: a cluster where
 * nothing is an ordinary round avatar looks like a novelty, and a polygon
 * with enough vertices to pass for a circle is more work for the same
 * result.
 *
 * No two entries here may look alike, which is a stronger requirement than
 * being different shapes. This list once held a four-sided polygon at 0.30
 * rounding and another at 0.46 — different by every measure the code has,
 * and the same squircle to look at, so a header drew two people in what
 * read as one shape while every rule was being obeyed. Deduplicating the
 * indices cannot fix that; the list itself has to be honest.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun materialShapeSet(): List<Shape> = SHAPE_SPECS.indices.map { materialShapeAt(it) }

/**
 * One entry of [materialShapeSet], for a caller that needs only the one —
 * which is every avatar.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun materialShapeAt(index: Int): Shape = POLYGONS[index]?.toShape() ?: CircleShape

/**
 * The set's polygons, built once for the process. They are pure functions of
 * their index, and they used to be built — rounded, normalised — inside every
 * composable that asked for the set: every row of the chat list and every
 * avatar beside a message made the whole set on first appearing, to keep
 * one shape of it. Null where the entry is the plain circle.
 */
private val POLYGONS: List<RoundedPolygon?> by lazy {
    SHAPE_SPECS.indices.map { index ->
        if (SHAPE_SPECS[index] is ShapeSpec.Circle) null else materialPolygon(index)
    }
}

/** How many shapes [materialShapeSet] holds. */
internal val SHAPE_COUNT: Int get() = SHAPE_SPECS.size

/** One entry of the set, as the recipe it is built from. */
private sealed interface ShapeSpec {
    data object Circle : ShapeSpec
    data class Polygon(val vertices: Int, val rounding: Float) : ShapeSpec
    data class Star(val points: Int, val innerRatio: Float, val rounding: Float) : ShapeSpec
}

/**
 * The set, in its order — which is part of what it is: a person's shape is
 * an index into this list, so reordering it re-shapes everybody.
 */
private val SHAPE_SPECS: List<ShapeSpec> = listOf(
    ShapeSpec.Circle,
    ShapeSpec.Star(points = 4, innerRatio = 0.75f, rounding = 0.50f),
    ShapeSpec.Polygon(vertices = 4, rounding = 0.30f),
    ShapeSpec.Star(points = 6, innerRatio = 0.78f, rounding = 0.48f),
    ShapeSpec.Polygon(vertices = 3, rounding = 0.24f),
    ShapeSpec.Star(points = 8, innerRatio = 0.82f, rounding = 0.44f),
    ShapeSpec.Polygon(vertices = 5, rounding = 0.14f),
    ShapeSpec.Star(points = 5, innerRatio = 0.70f, rounding = 0.40f),
    ShapeSpec.Polygon(vertices = 6, rounding = 0.12f),
    ShapeSpec.Star(points = 12, innerRatio = 0.88f, rounding = 0.40f),
    ShapeSpec.Star(points = 3, innerRatio = 0.62f, rounding = 0.44f),
    ShapeSpec.Polygon(vertices = 8, rounding = 0.10f)
)

/**
 * Entry [index] of the set as a polygon in the unit square — what a morph
 * needs, where clipping needs a [Shape]. The circle is a twelve-sided one
 * rounded all the way, so it can morph like the rest.
 */
internal fun materialPolygon(index: Int): RoundedPolygon =
    when (val spec = SHAPE_SPECS[index]) {
        ShapeSpec.Circle -> RoundedPolygon.circle(numVertices = 12).normalized()
        is ShapeSpec.Polygon -> regularPolygon(spec.vertices, spec.rounding)
        is ShapeSpec.Star -> starPolygon(spec.points, spec.innerRatio, spec.rounding)
    }

/**
 * A regular polygon.
 *
 * [rounding] is a fraction of the distance to the neighbouring vertex: 0 is a
 * hard corner, and values approaching 1 round the shape away into a circle.
 * Keep it low. The first version of this list used sixes, sevens and nines at
 * 0.35 to 0.80, and at the size an avatar is drawn every one of them came out
 * indistinguishable from a circle — the shapes were applied correctly and
 * showed nothing, which is the worst way for this to fail.
 *
 * Smoothing stays at its maximum: unsmoothed, a rounded corner meets the
 * straight edge with a visible kink at this size.
 */
private fun regularPolygon(vertices: Int, rounding: Float): RoundedPolygon =
    RoundedPolygon(
        numVertices = vertices,
        radius = 1f,
        centerX = 0f,
        centerY = 0f,
        rounding = CornerRounding(radius = rounding, smoothing = 1f)
    ).normalized()

/**
 * A star, which is what Material's cookies and flowers actually are.
 *
 * Built from explicit vertices because `graphics-shapes` publishes a
 * constructor taking a polygon's points but no star factory this project can
 * see — and a scalloped outline is most of what makes the shape library worth
 * showing. Points alternate between the outer radius and [innerRatio] of it;
 * a ratio near 1 gives the soft scallop of a cookie, lower gives the spikes
 * of a flower or a burst.
 *
 * The rounding applies to every point, inner and outer alike, which is what
 * keeps the scallops round rather than sharp.
 */
private fun starPolygon(points: Int, innerRatio: Float, rounding: Float): RoundedPolygon {
    val corners = points * 2
    val vertices = FloatArray(corners * 2)
    for (corner in 0 until corners) {
        val radius = if (corner % 2 == 0) 1f else innerRatio
        val angle = PI * corner / points
        vertices[corner * 2] = radius * cos(angle).toFloat()
        vertices[corner * 2 + 1] = radius * sin(angle).toFloat()
    }
    return RoundedPolygon(
        vertices = vertices,
        rounding = CornerRounding(radius = rounding, smoothing = 1f)
    ).normalized()
}

/**
 * Whether people get shapes, from the Appearance setting.
 *
 * Provided once at the root rather than passed to every screen that draws
 * an avatar: the conversation, its info screen and the pickers all draw
 * people, and none of them otherwise had any reason to know about settings.
 */
val LocalShapedAvatars = staticCompositionLocalOf { true }

/**
 * The shape a person or chat is drawn in, everywhere.
 *
 * The same seed gives the same shape as in the chat list, so the clover
 * someone is in the list is the clover they are in their conversation's
 * header, beside their messages, and in a group's member list — which is
 * the reason for giving people shapes at all. A circle when the setting is
 * off.
 */
@Composable
fun personShape(seed: Long): Shape {
    if (!LocalShapedAvatars.current) return CircleShape
    return materialShapeAt(avatarShapeIndex(seed, SHAPE_COUNT))
}
