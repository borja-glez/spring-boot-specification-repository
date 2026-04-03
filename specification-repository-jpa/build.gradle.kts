plugins {
    id("specification-boot3-library-conventions")
    id("specification-publish-conventions")
}

dependencies {
    api(project(":specification-repository-core"))
    api(libs.spring.data.jpa)
    implementation(libs.jakarta.persistence.api)
    implementation(libs.spring.context)
    implementation(libs.spring.core)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.data.jpa)
    testImplementation(libs.h2)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.postgresql)
}

