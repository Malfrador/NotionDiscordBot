plugins {
    id("java")
    id("com.gradleup.shadow") version "9.0.0-beta8"
}

group = "de.malfrador"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.seratch:notion-sdk-jvm-core:1.11.1")
    implementation("ch.qos.logback:logback-classic:1.5.16")
    implementation("net.dv8tion:JDA:5.3.0") {
        exclude(module="opus-java")
    }
    implementation("org.spongepowered:configurate-yaml:4.0.0");
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "de.malfrador.Main"
        )
    }
}

tasks.shadowJar {
    archiveFileName.set(project.name + ".jar")
}

