plugins {
    id("specification-boot3-application-conventions")
}

// Inside this build the library modules are projects, not downloaded artifacts: map their
// published coordinates to the projects so the example reads like a consuming application.
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        listOf("boot3-starter", "http").forEach { module ->
            substitute(module("com.borjaglez.specrepository:specification-repository-$module"))
                .using(project(":specification-repository-$module"))
        }
    }
}

dependencies {
    // Same shape as an application consuming the published artifacts: import the BOM once and
    // declare the modules without versions.
    implementation(platform(project(":specification-repository-bom")))
    implementation("com.borjaglez.specrepository:specification-repository-boot3-starter")
    implementation("com.borjaglez.specrepository:specification-repository-http")
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    developmentOnly(libs.spring.boot.devtools)
    runtimeOnly(libs.h2)
}
