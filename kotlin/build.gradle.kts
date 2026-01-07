plugins {
    kotlin("jvm") version "2.2.20"
    application
}

group = "org.shiyi"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    // WebSocket support
    implementation("org.java-websocket:Java-WebSocket:1.5.6")
    // JSON parsing
    implementation("org.json:json:20231013")
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    // Kotlin logging wrapper
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("AutoEvaluationKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

// 创建可执行 JAR
tasks.jar {
    archiveBaseName.set("zzuli-evaluation")
    archiveVersion.set("")
    manifest {
        attributes["Main-Class"] = "AutoEvaluationKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}
