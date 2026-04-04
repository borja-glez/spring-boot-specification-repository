plugins {
    java
    id("specification-java-base-conventions")
}

val libs = the<VersionCatalogsExtension>().named("libs")
val springBoot4Version = libs.findVersion("spring-boot4").get().requiredVersion

val developmentOnly: Configuration by configurations.creating
configurations.runtimeClasspath { extendsFrom(developmentOnly) }

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBoot4Version"))
    developmentOnly(platform("org.springframework.boot:spring-boot-dependencies:$springBoot4Version"))
    testImplementation(libs.findLibrary("spring-boot-starter-test").get())
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:$springBoot4Version"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
