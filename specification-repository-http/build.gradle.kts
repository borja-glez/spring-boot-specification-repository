plugins {
    id("specification-library-conventions")
    id("specification-publish-conventions")
}

dependencies {
    api(project(":specification-repository-core"))
    compileOnlyApi(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))
    compileOnlyApi(libs.spring.web)
    compileOnlyApi(libs.spring.webmvc)
    compileOnlyApi(libs.spring.boot.autoconfigure)
    annotationProcessor(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))
    annotationProcessor(libs.spring.boot.configuration.processor)
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))
    testImplementation(libs.spring.data.commons)
    testImplementation(libs.spring.web)
    testImplementation(libs.spring.webmvc)
    testImplementation(libs.spring.boot.autoconfigure)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.web)
}

// The configuration processor only reads the Javadoc of the classes it compiles: an incremental
// compile regenerates spring-configuration-metadata.json without the descriptions of the property
// classes it skipped, and that output would then be cached. Compile the module in full every time.
tasks.compileJava {
    options.isIncremental = false
}
