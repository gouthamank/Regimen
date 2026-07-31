package dev.gouthaman.regimen.sync.firestore

/** Schema-evolution guardrail for every `*Dto.toEntity()` reverse mapper: Firestore has no formal
 * migration mechanism, so an old app version's mapper shouldn't crash "Pull cloud data" entirely
 * just because a newer app version wrote an enum value it doesn't recognize yet - falls back to
 * [default] instead of throwing, the way a plain `Enum.valueOf(value)` would. */
internal inline fun <reified T : Enum<T>> parseEnumOrDefault(value: String, default: T): T =
    enumValues<T>().find { it.name == value } ?: default
