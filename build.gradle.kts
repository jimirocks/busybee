plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.serialization") version "2.3.10"
}

group = "rocks.jimi"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-client-core:3.4.0")
    implementation("io.ktor:ktor-client-cio:3.4.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.4.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.0")
    
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    
    implementation("com.google.api-client:google-api-client:2.8.1")
    implementation("com.google.apis:google-api-services-calendar:v3-rev20251207-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.42.1")
    
    implementation("com.sun.mail:javax.mail:1.6.2")
    
    implementation("org.slf4j:slf4j-simple:2.0.9")
    
    implementation("org.yaml:snakeyaml:2.2")
    
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
    
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnit()
}

tasks.register<Jar>("fatJar") {
    archiveBaseName.set("calsync")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath.get().map { zipTree(it) }
    })
    manifest {
        attributes["Main-Class"] = "rocks.jimi.calsync.MainKt"
    }
}
