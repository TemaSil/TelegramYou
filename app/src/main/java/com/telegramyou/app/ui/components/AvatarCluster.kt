package com.telegramyou.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.graphics.shapes.RoundedPolygon
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
data class ClusterMember(val name: String, val seed: Long)

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
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun materialShapeSet(): List<Shape> = listOf(
    CircleShape,
    starShape(points = 4, innerRatio = 0.75f, rounding = 0.50f),
    polygonShape(vertices = 4, rounding = 0.16f),
    starShape(points = 6, innerRatio = 0.78f, rounding = 0.48f),
    polygonShape(vertices = 3, rounding = 0.24f),
    starShape(points = 8, innerRatio = 0.82f, rounding = 0.44f),
    polygonShape(vertices = 5, rounding = 0.14f),
    starShape(points = 5, innerRatio = 0.70f, rounding = 0.40f),
    polygonShape(vertices = 6, rounding = 0.12f),
    starShape(points = 12, innerRatio = 0.88f, rounding = 0.40f),
    polygonShape(vertices = 4, rounding = 0.46f),
    polygonShape(vertices = 8, rounding = 0.10f)
)

/**
 * A regular polygon, as a [Shape] an avatar can be clipped to.
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun polygonShape(vertices: Int, rounding: Float): Shape =
    remember(vertices, rounding) {
        RoundedPolygon(
            numVertices = vertices,
            radius = 1f,
            centerX = 0f,
            centerY = 0f,
            rounding = CornerRounding(radius = rounding, smoothing = 1f)
        ).normalized()
    }.toShape()

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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun starShape(points: Int, innerRatio: Float, rounding: Float): Shape =
    remember(points, innerRatio, rounding) {
        val corners = points * 2
        val vertices = FloatArray(corners * 2)
        for (corner in 0 until corners) {
            val radius = if (corner % 2 == 0) 1f else innerRatio
            val angle = PI * corner / points
            vertices[corner * 2] = radius * cos(angle).toFloat()
            vertices[corner * 2 + 1] = radius * sin(angle).toFloat()
        }
        RoundedPolygon(
            vertices = vertices,
            rounding = CornerRounding(radius = rounding, smoothing = 1f)
        ).normalized()
    }.toShape()
