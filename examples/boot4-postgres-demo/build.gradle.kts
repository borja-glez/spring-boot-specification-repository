plugins {
    id("specification-boot4-application-conventions")
}

dependencies {
    implementation(project(":specification-repository-boot4-starter"))
    implementation(libs.spring.boot4.starter.web)
    implementation(libs.spring.boot4.starter.data.jpa)
    implementation(libs.spring.boot4.docker.compose)
    developmentOnly(libs.spring.boot4.devtools)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.spring.boot4.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
