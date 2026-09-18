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
 * **Nobody in the cluster repeats.** Two identical outlines read as one
 * shape worn twice rather than as two people, and that holds whether they
 * are side by side or separated by somebody else — the first version only
 * separated neighbours, and a header showing hexagon, circle, hexagon looked
 * like a bug rather than like a rule being followed. Where two seeds want
 * the same shape, the later one walks along the list to the next free one,
 * so the first person named keeps what is theirs.
 *
 * With more people than shapes the rule has to give, and it gives in the
 * smallest way available: repeats become possible again, but never between
 * neighbours.
 */
fun avatarShapeIndices(seeds: List<Long>, shapeCount: Int): List<Int> {
    require(shapeCount > 0) { "a cluster needs at least one shape to draw with" }
    val indices = ArrayList<Int>(seeds.size)
    val taken = HashSet<Int>()
    for (seed in seeds) {
        val preferred = avatarShapeIndex(seed, shapeCount)

        // Walk to the next shape nobody in this cluster is wearing. Bounded
        // by the number of shapes, so a cluster with more people than shapes
        // stops rather than circling forever.
        var index = preferred
        var steps = 0
        while (index in taken && steps < shapeCount) {
            index = (index + 1) % shapeCount
            steps++
        }

        if (index in taken) {
            // Everything is spoken for. Fall back to what this person would
            // have had, and only insist on the one thing still worth
            // insisting on: not the same as the avatar beside them.
            index = preferred
            if (shapeCount > 1 && indices.lastOrNull() == index) {
                index = (index + 1) % shapeCount
            }
        }

        taken.add(index)
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
