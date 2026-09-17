package com.telegramyou.app.ui.avatars

/**
 * Which shape each avatar in a cluster wears.
 *
 * Indices rather than shapes, because the shapes themselves are Android
 * types and this module has none. The caller holds the list and looks the
 * index up in it — see `AvatarCluster` in :app.
 *
 * Two rules, and they pull against each other:
 *
 * **A person keeps their shape.** Derived from the same seed the avatar's
 * colour already uses, so somebody is a clover in every group they are in,
 * and stays one across a reload. A shape reshuffled on each recomposition
 * would be decoration; one that belongs to a person is recognition, which is
 * the whole argument Material makes for the shape library.
 *
 * **Neighbours differ.** Two identical shapes side by side in a cluster read
 * as one wider blob rather than two people. Where the seeds collide modulo
 * the shape count, the second is nudged along — so the second of a pair
 * loses its usual shape, and the first keeps it. That is the right way round:
 * the leftmost avatar is the one the eye lands on.
 */
fun avatarShapeIndices(seeds: List<Long>, shapeCount: Int): List<Int> {
    require(shapeCount > 0) { "a cluster needs at least one shape to draw with" }
    val indices = ArrayList<Int>(seeds.size)
    for (seed in seeds) {
        val preferred = avatarShapeIndex(seed, shapeCount)
        // With one shape there is nothing to nudge towards, and the loop
        // below would spin looking for a different one.
        val index = if (shapeCount > 1 && indices.lastOrNull() == preferred) {
            (preferred + 1) % shapeCount
        } else {
            preferred
        }
        indices.add(index)
    }
    return indices
}

/**
 * The shape a seed asks for, ignoring its neighbours.
 *
 * The double modulo is not redundant: Kotlin's `%` keeps the sign of the
 * left operand, so a negative seed — and ids from Telegram are signed —
 * would otherwise index backwards off the start of the list.
 */
fun avatarShapeIndex(seed: Long, shapeCount: Int): Int {
    require(shapeCount > 0) { "a cluster needs at least one shape to draw with" }
    return (((seed % shapeCount) + shapeCount) % shapeCount).toInt()
}
