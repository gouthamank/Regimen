import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

// The Kotlin JVM plugin also applies the Java plugin, whose compileJava task otherwise defaults to
// JavaVersion.current() (whatever JDK runs Gradle) - pin it to match compileKotlin's jvmTarget below,
// or Gradle fails with "Inconsistent JVM-target compatibility" between compileJava and compileKotlin.
extensions.configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}
