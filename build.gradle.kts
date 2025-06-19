plugins {
    kotlin("jvm") version "1.9.0"
    `maven-publish`
    id("com.github.breadmoirai.github-release") version "2.5.2"
}

project.group = "io.github.dkoontz"
project.version = "0.1.3"

repositories { mavenCentral() }

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(11) }

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

tasks.test { useJUnitPlatform() }

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
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

tasks.register("checkTagMatchesVersion") {
    group = "verification"
    description = "Fails if the provided local_ref tag does not match project.version."
    doLast {
        val localRef = project.findProperty("local_ref") as String?
        if (localRef == null) {
            throw GradleException("❌ local_ref tag was not provided. Please provide it using -Plocal_ref=refs/tags/v0.1.0")
        }
        if (localRef.startsWith("refs/tags/")) {
            val tag = localRef.removePrefix("refs/tags/")
            if (tag.startsWith("v")) {
                val expectedTag = "v${project.version}"
                if (tag != expectedTag) {
                    throw GradleException("❌ Tag being pushed '$tag' does not match project.version '$expectedTag'")
                } else {
                    println("✔︎ Tag matches project.version: $tag == $expectedTag")
                }
            }
        }
        
    }
}

tasks.register<Copy>("installGitHooks") {
    group = "build setup"
    description = "Copies pre-push hook script to .git/hooks/pre-push (Windows and Unix)"
    val gitHooksDir = file(".git/hooks")
    
    val hookSourceFile = file("scripts/git-hooks/pre-push/pre-push.sh")
    
    if (!gitHooksDir.exists()) {
        gitHooksDir.mkdirs()
    }

    from(hookSourceFile)
    into(gitHooksDir)
    
    rename { "pre-push" }

    doLast {
        if (!System.getProperty("os.name").lowercase().contains("win")) {
            val hookDestinationFile = file(".git/hooks/pre-push")
            hookDestinationFile.setExecutable(true)
        }
        println("Git pre-push hook installed.")
    }
}

tasks.named("build") {
    dependsOn("installGitHooks")
}

tasks.named("githubRelease") {
    dependsOn("jar")
}
tasks.named("publish") {
    dependsOn("compileKotlin")
}
