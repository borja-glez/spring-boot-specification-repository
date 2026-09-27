plugins {
    java
    id("io.freefair.lombok")
    id("com.diffplug.spotless")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        importOrder("java", "javax", "jakarta", "org", "com", "io")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-parameters"))
}

// Tests run on the build toolchain (Java 21) unless -PtestJavaVersion=<n> selects another runtime.
// Compilation is unaffected: bytecode keeps targeting Java 21.
val testJavaVersion = providers.gradleProperty("testJavaVersion")
val javaToolchains = extensions.getByType<JavaToolchainService>()

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    if (testJavaVersion.isPresent) {
        javaLauncher.set(
            javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(testJavaVersion.get()))
            },
        )
    }
}
