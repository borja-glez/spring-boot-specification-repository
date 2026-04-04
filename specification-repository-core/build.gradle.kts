plugins {
    id("specification-library-conventions")
    id("specification-publish-conventions")
}

dependencies {
    compileOnlyApi(libs.spring.data.commons)

    testImplementation(libs.spring.data.commons)
}
