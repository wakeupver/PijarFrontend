import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Modul murni Kotlin/JVM (tanpa Android) supaya logika Daijishō-compat bisa diuji cepat di CI.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
