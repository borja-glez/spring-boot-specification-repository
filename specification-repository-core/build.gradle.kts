plugins {
    id("specification-library-conventions")
    id("specification-publish-conventions")
    id("specification-mutation-conventions")
}

dependencies {
    compileOnlyApi(libs.spring.data.commons)

    testImplementation(libs.spring.data.commons)
}
