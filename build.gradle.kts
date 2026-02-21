plugins {
    kotlin("jvm") version "1.9.0"
    `maven-publish`
    id("com.github.breadmoirai.github-release") version "2.5.2"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
}

project.group = "io.github.dkoontz"

project.version = "0.1.8"

repositories { mavenCentral() }

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

kotlin { jvmToolchain(17) }

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

tasks.test { useJUnitPlatform() }

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            // Ensure dependencies are included in the published POM
            pom {
                withXml {
                    val dependenciesNode = asNode().appendNode("dependencies")
                    configurations.api.get().dependencies.forEach { dep ->
                        val dependencyNode = dependenciesNode.appendNode("dependency")
                        dependencyNode.appendNode("groupId", dep.group)
                        dependencyNode.appendNode("artifactId", dep.name)
                        dependencyNode.appendNode("version", dep.version)
                        dependencyNode.appendNode("scope", "compile")
                    }
                }
            }
        }
    }
}

githubRelease {
    token(System.getenv("GITHUB_TOKEN") ?: "")
    owner.set("dkoontz")
    repo.set(project.name)
    tagName.set("v${project.version}")
    releaseName.set("Release ${project.version}")
    targetCommitish.set("main")
    body.set("Automated release for version ${project.version}")
    releaseAssets.setFrom(file("build/libs/${project.name}-${project.version}.jar"))
    draft.set(false)
    prerelease.set(false)
}

tasks.named("githubRelease") { dependsOn("jar") }

tasks.named("publish") { dependsOn("compileKotlin") }

// Run ktlint formatting before compilation
tasks.named("compileKotlin") { dependsOn("ktlintFormat") }

tasks.named("compileTestKotlin") { dependsOn("ktlintFormat") }

// Install git pre-push hook that works cross-platform
tasks.register("installGitHooks") {
    group = "build setup"
    description = "Installs git pre-push hook for ktlint checking"

    val hooksDir = file(".git/hooks")
    val prePushHook = file(".git/hooks/pre-push")

    outputs.file(prePushHook)

    doLast {
        if (!hooksDir.exists()) {
            hooksDir.mkdirs()
        }

        val isWindows = System.getProperty("os.name").lowercase().contains("windows")

        if (isWindows) {
            // Windows batch script - works with native Windows Git and IntelliJ
            prePushHook.writeText(
                """
                @echo off
                echo Running ktlint check...
                call gradlew.bat ktlintCheck --quiet
                if errorlevel 1 (
                    echo.
                    echo ktlint check failed.
                    echo Please run 'gradlew.bat ktlintFormat' and commit the changes before pushing.
                    exit /b 1
                )
                echo ktlint check passed.
                """.trimIndent(),
            )
        } else {
            // Unix shell script - works on macOS, Linux
            prePushHook.writeText(
                """
                #!/bin/sh
                echo "Running ktlint check..."
                ./gradlew ktlintCheck --quiet
                if [ ${'$'}? -ne 0 ]; then
                    echo ""
                    echo "ktlint check failed."
                    echo "Please run './gradlew ktlintFormat' and commit the changes before pushing."
                    exit 1
                fi
                echo "ktlint check passed."
                """.trimIndent(),
            )
            prePushHook.setExecutable(true)
        }

        println("Git pre-push hook installed for ${if (isWindows) "Windows" else "Unix"}")
    }
}

tasks.named("build") { dependsOn("installGitHooks") }
