plugins {
    id("specification-java-conventions")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    // @API stability annotations: needed to compile the library, not at runtime (as in JUnit 5).
    compileOnlyApi(libs.findLibrary("apiguardian-api").get())
    testImplementation(libs.findLibrary("apiguardian-api").get())
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
    testImplementation(libs.findLibrary("assertj-core").get())
    testImplementation(libs.findLibrary("mockito-junit-jupiter").get())
}
