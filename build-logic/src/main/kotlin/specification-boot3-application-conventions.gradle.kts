plugins {
    java
    id("specification-java-base-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val libs = the<VersionCatalogsExtension>().named("libs")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    testImplementation(libs.findLibrary("spring-boot-starter-test").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
