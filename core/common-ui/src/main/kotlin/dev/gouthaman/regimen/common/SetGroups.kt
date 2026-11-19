package dev.gouthaman.regimen.common

import dev.gouthaman.regimen.domain.model.SetEntry

/** A run of consecutive sets at the same weight, for compact summaries like "60 kg · 10, 10, 8".
 * [weightKg] is null for bodyweight sets. */
data class SetGroup(val weightKg: Double?, val reps: List<Int?>) {
    /** More than one set, all with the same known rep count - shown as "60 kg · 4 × 10". */
    val isUniform: Boolean
        get() = reps.size > 1 && reps.first() != null && reps.all { it == reps.first() }
}

/** Groups consecutive sets (by set number) by weight. Order is kept, so 60/65/60 is three groups,
 * not two - the summary reads in the order the sets were lifted. */
fun groupSetsByWeight(sets: List<SetEntry>): List<SetGroup> = buildList {
    for (set in sets.sortedBy { it.setNumber }) {
        val last = lastOrNull()
        if (last != null && last.weightKg == set.weightKg) {
            set(lastIndex, last.copy(reps = last.reps + set.reps))
        } else {
            add(SetGroup(set.weightKg, listOf(set.reps)))
        }
    }
}
