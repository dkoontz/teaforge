plugins {
    kotlin("jvm") version "1.9.0"
    `maven-publish`
}

group = "io.github.teaforge"

version = "0.1.0"

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
            groupId = "io.github.teaforge"
            artifactId = "teaforge"
            version = "0.1.0"

            from(components["java"])
        }
    }
}
