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
 * So they are made from `androidx.graphics:graphics-shapes`, which is the
 * library the Material catalogue is itself built on and, unlike the
 * catalogue, a stable 1.0.1. Regular polygons with a vertex count and a
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
    polygonShape(vertices = 4, rounding = 0.30f),
    polygonShape(vertices = 6, rounding = 0.80f),
    polygonShape(vertices = 5, rounding = 0.22f),
    polygonShape(vertices = 3, rounding = 0.40f),
    polygonShape(vertices = 8, rounding = 0.26f),
    polygonShape(vertices = 12, rounding = 0.50f),
    polygonShape(vertices = 4, rounding = 0.14f),
    polygonShape(vertices = 7, rounding = 0.35f),
    polygonShape(vertices = 6, rounding = 0.20f),
    polygonShape(vertices = 9, rounding = 0.45f),
    polygonShape(vertices = 5, rounding = 0.60f)
)

/**
 * One regular polygon, as a [Shape] an avatar can be clipped to.
 *
 * [rounding] is a fraction of the distance to the neighbouring vertex, so 0
 * is a hard corner and values near 1 round the shape away towards a circle.
 * Smoothing is left at its maximum: an unsmoothed rounding meets the
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
