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

application {
    mainClass = "dev.exercisevalidator.Main"
}

tasks.test {
    useJUnitPlatform()
}
