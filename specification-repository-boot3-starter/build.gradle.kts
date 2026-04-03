plugins {
    id("specification-boot3-library-conventions")
    id("specification-publish-conventions")
}

dependencies {
    api(project(":specification-repository-jpa"))
    api(libs.spring.boot.autoconfigure)
    annotationProcessor(libs.spring.boot.configuration.processor)
    testImplementation(libs.spring.boot.starter.test)
}
