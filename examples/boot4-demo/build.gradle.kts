plugins {
    id("specification-boot4-application-conventions")
}

dependencies {
    implementation(project(":specification-repository-boot4-starter"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    developmentOnly(libs.spring.boot.devtools)
    runtimeOnly(libs.h2)
    testRuntimeOnly(libs.junit.platform.launcher)
}
