plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij") version "1.17.4"
}

dependencies {
    implementation(project(":core"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("io.kotest:kotest-runner-junit5:5.7.2")
    testImplementation("io.kotest:kotest-assertions-core:5.7.2")
}

intellij {
    version.set("2023.2.5")
    type.set("IC") // IntelliJ IDEA Community Edition

    plugins.set(listOf(
        "java",
        "Kotlin"
    ))
}

tasks {
    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("")
    }

    // buildSearchableOptions runs a headless IDE to index plugin settings for the
    // Settings search bar. It hits a ConcurrentModificationException in IntelliJ
    // internals (known issue with intellij-gradle-plugin 1.17.x). Disabled because
    // settings search is non-critical and the task is not needed for local development.
    named("buildSearchableOptions") {
        enabled = false
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
