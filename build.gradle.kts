plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.androidx.room3) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.aboutlibraries.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktfmt) apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

subprojects {
    plugins.apply("com.ncorti.ktfmt.gradle")

    configure<com.ncorti.ktfmt.gradle.KtfmtExtension> {
        kotlinLangStyle()
    }

    // The formatter version, instead of the plugin's default (see libs.versions.toml). The plugin
    // adds its own version to the configuration, so this one is forced.
    val ktfmtFormatter = rootProject.libs.ktfmt.formatter.get().toString()
    configurations.named("ktfmt") { resolutionStrategy.force(ktfmtFormatter) }

    // The plugin's scripts tasks take every .kts file under the module folder, build/ included, so
    // they read the build outputs while a build in the same Gradle call writes them and fail on a
    // folder that disappears (#663). Only the module's own build scripts are formatted.
    listOf("ktfmtCheckScripts", "ktfmtFormatScripts").forEach { name ->
        tasks.named<SourceTask>(name) { setSource(fileTree(projectDir) { include("*.gradle.kts") }) }
    }

    // ktfmtCheck once passed while checking no Kotlin file at all, only the build scripts: the
    // plugin found no source sets under AGP 9's built-in Kotlin (#661). This fails ktfmtCheck when
    // a .kt file under src/ is not in any of the plugin's source-set tasks.
    val ktfmtSourceSetChecks =
        tasks.withType<com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask>().matching {
            it.name != "ktfmtCheckScripts"
        }
    val ktfmtCheckCoverage =
        tasks.register("ktfmtCheckCoverage") {
            description = "Fails if ktfmtCheck leaves out a Kotlin file under src/."
            val kotlinFiles = fileTree("src") { include("**/*.kt") }
            val checkedFiles = files(provider { ktfmtSourceSetChecks.map { it.source } })
            val projectDir = layout.projectDirectory
            inputs.files(kotlinFiles)
            inputs.files(checkedFiles)
            doLast {
                val missed = kotlinFiles.files - checkedFiles.files
                if (missed.isNotEmpty()) {
                    val list =
                        missed.map { it.relativeTo(projectDir.asFile).invariantSeparatorsPath }.sorted()
                    throw GradleException(
                        "ktfmtCheck doesn't check ${missed.size} Kotlin file(s), such as:\n" +
                            list.take(10).joinToString("\n") { "  $it" }
                    )
                }
            }
        }
    tasks.named("ktfmtCheck") { dependsOn(ktfmtCheckCoverage) }

    // A module with product flavors has no testDebugUnitTest, only test<Flavor>DebugUnitTest, so
    // `./gradlew testDebugUnitTest` skipped app:phone, app:tv and player:cast (#515). This adds
    // the task to those modules, running the debug unit tests of every flavor.
    listOf("com.android.application", "com.android.library").forEach { id ->
        pluginManager.withPlugin(id) {
            extensions
                .getByType(com.android.build.api.variant.AndroidComponentsExtension::class.java)
                .onVariants { variant ->
                    if (
                        variant.flavorName.isNullOrEmpty() ||
                            "testDebugUnitTest" in tasks.names
                    )
                        return@onVariants
                    val flavorTests = Regex("test[A-Z]\\w*DebugUnitTest")
                    tasks.register("testDebugUnitTest") {
                        group = "verification"
                        description = "Runs the debug unit tests of every flavor."
                        // Names only: realizing every task fails on the Compose preview tasks.
                        dependsOn(provider { tasks.names.filter { flavorTests.matches(it) } })
                    }
                }
        }
    }

    // The unit tests run on the JUnit Platform (#699). The tests are still JUnit 4 and run through
    // the Vintage engine; Jupiter is there for tests that move to it. The launcher is listed
    // because Gradle 9 no longer adds it. Set here, so no module can run on the platform without
    // the engines (and then find no tests). The assertions are kotlin.test's, which work under
    // both engines.
    listOf("com.android.application", "com.android.library").forEach { id ->
        pluginManager.withPlugin(id) {
            val libs = rootProject.libs
            dependencies {
                "testImplementation"(platform(libs.junit.bom))
                "testImplementation"(libs.junit.jupiter)
                "testImplementation"(libs.kotlin.test)
                "testRuntimeOnly"(libs.junit.vintage.engine)
                "testRuntimeOnly"(libs.junit.platform.launcher)
            }
            tasks.withType<Test>().configureEach { useJUnitPlatform() }
        }
    }
}

tasks.register<Delete>("clean") {
    description = "Deletes all build artifacts."
    delete(rootProject.layout.buildDirectory)
}
