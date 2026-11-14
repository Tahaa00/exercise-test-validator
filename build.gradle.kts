plugins {
    application
}

group = "dev.exercisevalidator"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    // The tool itself targets Java 17, the oldest JDK tutors use, so it runs on any JDK they have.
    // The exercises it checks pick their own Java version through their own toolchain.
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    // Sources contain non-ASCII text. Without this, JDK 17 compiles them in the platform encoding,
    // which is not UTF-8 on every machine (for example a CI container with the POSIX locale).
    options.encoding = "UTF-8"
}

application {
    mainClass = "dev.exercisevalidator.Main"
}

tasks.test {
    // Fast unit tests only: no Gradle runs, no network.
    useJUnitPlatform { excludeTags("integration") }
}

// Runs the validator end to end on real Gradle builds (the plain sample and a flaky fixture).
// Slow, needs JDK 17 and network access for the first dependency download, so it is a separate task.
tasks.register<Test>("integrationTest") {
    description = "Runs the validator against real exercise builds."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
    shouldRunAfter(tasks.test)
}
