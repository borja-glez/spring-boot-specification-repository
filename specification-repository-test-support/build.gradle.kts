plugins {
    id("specification-library-conventions")
}

dependencies {
    api(platform(libs.testcontainers.bom))
    api(libs.testcontainers.junit)
    api(libs.testcontainers.postgresql)
}
