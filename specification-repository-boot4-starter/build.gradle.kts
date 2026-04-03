plugins {
    id("specification-library-conventions")
    id("specification-publish-conventions")
}

dependencies {
    api(project(":specification-repository-jpa"))
    api(libs.spring.boot4.autoconfigure)
    annotationProcessor(libs.spring.boot4.configuration.processor)
    testImplementation(libs.spring.boot4.starter.test)
}
