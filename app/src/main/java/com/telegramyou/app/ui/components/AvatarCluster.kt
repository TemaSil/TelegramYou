package com.telegramyou.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
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
 * A chosen subset of the thirty-five, not all of them. Several — `pill`,
 * `oval`, `semiCircle`, `arch` — are far from square, and an avatar built on
 * one comes out a different size from its neighbours, which reads as a
 * layout bug rather than as variety. These are the ones that fill a square.
 *
 * `circle` is deliberately first and deliberately present: a cluster where
 * nothing is a plain circle looks like a novelty, and one where the first
 * face is familiar does not.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun materialShapeSet(): List<Shape> = listOf(
    MaterialShapes.circle.toShape(),
    MaterialShapes.cookie4Sided.toShape(),
    MaterialShapes.clover4Leaf.toShape(),
    MaterialShapes.square.toShape(),
    MaterialShapes.sunny.toShape(),
    MaterialShapes.cookie6Sided.toShape(),
    MaterialShapes.pentagon.toShape(),
    MaterialShapes.flower.toShape(),
    MaterialShapes.gem.toShape(),
    MaterialShapes.burst.toShape(),
    MaterialShapes.puffy.toShape(),
    MaterialShapes.cookie9Sided.toShape()
)
