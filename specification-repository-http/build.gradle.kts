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
