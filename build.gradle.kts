plugins {
    kotlin("jvm") version "1.9.0"
    `maven-publish`
    id("com.github.breadmoirai.github-release") version "2.5.2"
}

project.group = "io.github.dkoontz"

project.version = "0.1.5"

repositories { mavenCentral() }

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
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
    tagName.set("${project.version}")
    releaseName.set("Release ${project.version}")
    targetCommitish.set("main")
    body.set("Automated release for version ${project.version}")
    releaseAssets.setFrom(file("build/libs/${project.name}-${project.version}.jar"))
    draft.set(false)
    prerelease.set(false)
}

tasks.named("githubRelease") { dependsOn("jar") }

tasks.named("publish") { dependsOn("compileKotlin") }
