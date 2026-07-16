package dev.gouthaman.regimen.domain.di

import javax.inject.Qualifier

/**
 * Qualifies the process-lifetime [kotlinx.coroutines.CoroutineScope] used for terminal writes
 * that must survive the ViewModel/Service that started them being torn down mid-flight (see
 * ActiveWorkoutViewModel's finish/discard). The actual `@Provides` lives in `:app`'s
 * `di/CoroutinesModule.kt` - only the qualifier itself needs to be reachable from every module
 * that injects it.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
