package ru.nezabudka.app.data

data class ManualOrderUpdate(
    val id: Long,
    val order: Long,
)

/**
 * Reuses the existing ordered slots so a drag updates only the moved range.
 * A normalized sequence is used only for legacy rows with duplicate order values.
 */
internal fun manualOrderUpdates(
    stored: List<Pair<Long, Long>>,
    movedId: Long,
    beforeId: Long?,
): List<ManualOrderUpdate> {
    val ordered = stored.sortedWith(compareBy<Pair<Long, Long>> { it.second }.thenBy { it.first })
    val moved = ordered.firstOrNull { it.first == movedId } ?: return emptyList()
    val reordered = ordered.toMutableList().apply {
        remove(moved)
        val target = beforeId
            ?.let { targetId -> indexOfFirst { it.first == targetId }.takeIf { it >= 0 } }
            ?: size
        add(target, moved)
    }
    if (reordered == ordered) return emptyList()

    val existingSlots = ordered.map(Pair<Long, Long>::second)
    val slots = if (existingSlots.zipWithNext().all { (left, right) -> left < right }) {
        existingSlots
    } else {
        List(ordered.size) { index -> index.toLong() }
    }
    val oldOrders = ordered.associate { value -> value.first to value.second }
    return reordered.mapIndexedNotNull { index, value ->
        slots[index].takeIf { it != oldOrders.getValue(value.first) }
            ?.let { order -> ManualOrderUpdate(value.first, order) }
    }
}
