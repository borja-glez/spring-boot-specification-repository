plugins {
    id("com.vanniktech.maven.publish")
}

// A java-platform (such as a BOM) has no jar to compare, so only projects with the java plugin get
// the binary compatibility check.
pluginManager.withPlugin("java") {
    apply(plugin = "specification-api-compatibility-conventions")
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    pom {
        name.set(project.name)
        description.set(project.findProperty("description")?.toString() ?: project.name)
        url.set("https://github.com/borja-glez/spring-boot-specification-repository")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("borjaglez")
                name.set("Borja Gonzalez Enriquez")
                url.set("https://github.com/borja-glez")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/borja-glez/spring-boot-specification-repository.git")
            developerConnection.set("scm:git:ssh://git@github.com/borja-glez/spring-boot-specification-repository.git")
            url.set("https://github.com/borja-glez/spring-boot-specification-repository")
        }
    }
}

afterEvaluate {
    extensions.findByType<SigningExtension>()?.isRequired = System.getenv("CI") != null
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        versionMapping {
            allVariants {
                fromResolutionResult()
            }
        }
    }
}

// Maven Central counts every uploaded file. It needs the artifacts, their .asc signatures and md5/sha1
// checksums of the artifacts; the checksums Gradle also writes for the signatures and the sha256/sha512
// ones are optional, so they are removed from the staging directory before the deployment is uploaded.
tasks.withType<PublishToMavenRepository>().configureEach {
    if (name.endsWith("ToMavenCentralRepository")) {
        val stagingDirectory = project.layout.buildDirectory.dir("publishing/mavenCentral")
        doLast {
            stagingDirectory.get().asFile.walkTopDown()
                .filter { file ->
                    file.isFile && (file.name.contains(".asc.") ||
                        file.name.endsWith(".sha256") || file.name.endsWith(".sha512"))
                }
                .forEach { it.delete() }
        }
    }
}

// A java-platform (such as a BOM) has no jar, so this applies only to projects with the java plugin.
pluginManager.withPlugin("java") {
    // Every published jar declares a stable JPMS name, so applications on the module path do not get one
    // derived from the file name. The name is the module's root package:
    // specification-repository-<x>[-starter] becomes com.borjaglez.specrepository.<x>.
    val automaticModuleName = "com.borjaglez.specrepository." +
        project.name.removePrefix("specification-repository-").removeSuffix("-starter")

    tasks.named<Jar>("jar") {
        manifest {
            attributes("Automatic-Module-Name" to automaticModuleName)
        }
    }

    val verifyAutomaticModuleName by tasks.registering {
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        description = "Verifies that the jar manifest declares the expected Automatic-Module-Name."
        val jarFile = tasks.named<Jar>("jar").flatMap { it.archiveFile }
        val expectedName = automaticModuleName
        inputs.file(jarFile)
        inputs.property("expectedName", expectedName)
        doLast {
            java.util.jar.JarFile(jarFile.get().asFile).use { jar ->
                val actualName = jar.manifest?.mainAttributes?.getValue("Automatic-Module-Name")
                check(actualName == expectedName) {
                    "${jar.name}: expected Automatic-Module-Name '$expectedName' but found '$actualName'"
                }
                val validName = expectedName.split('.').all { segment ->
                    segment.isNotEmpty() &&
                        Character.isJavaIdentifierStart(segment[0]) &&
                        segment.all(Character::isJavaIdentifierPart) &&
                        !javax.lang.model.SourceVersion.isKeyword(segment)
                }
                check(validName) { "${jar.name}: '$expectedName' is not a valid module name" }
                val packageDirectory = expectedName.replace('.', '/') + "/"
                val hasPackage = jar.entries().asSequence()
                    .any { it.name.startsWith(packageDirectory) && it.name.endsWith(".class") }
                check(hasPackage) {
                    "${jar.name}: Automatic-Module-Name '$expectedName' is not a package of the jar"
                }
            }
        }
    }

    tasks.named("check") {
        dependsOn(verifyAutomaticModuleName)
    }
}
