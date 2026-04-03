plugins {
    id("specification-boot4-application-conventions")
}

dependencies {
    implementation(project(":specification-repository-boot4-starter"))
    implementation(libs.spring.boot4.starter.web)
    implementation(libs.spring.boot4.starter.data.jpa)
    developmentOnly(libs.spring.boot4.devtools)
    runtimeOnly(libs.h2)
    testImplementation(libs.spring.boot4.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
