plugins {
    id("regimen.android.library.compose")
    id("regimen.android.hilt")
}

// Common feature-module project dependencies (:core:domain, :core:designsystem,
// :core:navigation-api) are added here once those modules exist (Phases 2, 5, 6) — they can't be
// referenced yet since none of them exist in settings.gradle.kts as of Phase 0.
