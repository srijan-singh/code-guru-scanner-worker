plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.0" // Use an appropriate version
}

group = "code.guru"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

intellij {
    version.set("2023.1")
    type.set("IC") // 'IC' = IntelliJ Community Edition
    plugins.set(listOf("java")) // Enable Java support
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}
