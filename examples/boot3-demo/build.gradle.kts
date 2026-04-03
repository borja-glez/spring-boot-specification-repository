plugins {
    id("specification-boot3-application-conventions")
}

dependencies {
    implementation(project(":specification-repository-boot3-starter"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    developmentOnly(libs.spring.boot.devtools)
    runtimeOnly(libs.h2)
}
