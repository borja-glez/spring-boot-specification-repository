plugins {
    id("specification-library-conventions")
    id("specification-publish-conventions")
}

dependencies {
    api(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))
    annotationProcessor(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))
    api(project(":specification-repository-jpa"))
    api(libs.spring.boot.autoconfigure)
    annotationProcessor(libs.spring.boot.configuration.processor)
    testImplementation(libs.spring.boot3.starter.data.jpa)
    testImplementation(libs.spring.data.jpa)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(project(":specification-repository-http"))
    testImplementation(libs.spring.boot.starter.web)
    testImplementation(libs.h2)
}
