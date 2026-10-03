import com.borjaglez.specrepository.gradle.ApiStatusBinaryCompatibilityRule
import me.champeau.gradle.japicmp.JapicmpTask

// Binary compatibility check of the published API against the release named by the `apiBaseline`
// property (gradle.properties or -PapiBaseline=<version>). See docs/cicd-workflow.md.
plugins {
    `java-library`
    id("me.champeau.gradle.japicmp")
}

val apiBaseline = providers.gradleProperty("apiBaseline").orNull?.trim().orEmpty()
val currentVersion = version.toString()

fun majorOf(version: String): Int =
    version.substringBefore('.').toIntOrNull()
        ?: throw GradleException("Cannot read the major version of '$version'")

// Skipped until a baseline is set, and on a new major, which may break the API.
val checkEnabled = apiBaseline.isNotEmpty() && majorOf(currentVersion) <= majorOf(apiBaseline)

tasks.register<JapicmpTask>("apiCompatibility") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Fails on binary-incompatible changes to the public API compared with apiBaseline."

    val enabled = checkEnabled
    onlyIf("apiBaseline is set and has the same major version as $currentVersion") { enabled }
    if (!enabled) {
        return@register
    }

    // The released jar alone, resolved from Maven Central: its dependencies are not compared.
    val baseline = configurations.detachedConfiguration(
        dependencies.create("${project.group}:${project.name}:$apiBaseline"),
    ).apply { isTransitive = false }

    oldArchives.from(baseline)
    newArchives.from(tasks.named("jar"))
    // Both sides are resolved against the current compile classpath (Spring, Jakarta, sibling modules).
    oldClasspath.from(configurations.named("compileClasspath"))
    newClasspath.from(configurations.named("compileClasspath"))

    // Protected members of public, non-final classes are part of the binary API for subclasses.
    accessModifier.set("protected")
    onlyModified.set(true)
    // The rich report below decides what fails, so japicmp's own (annotation-blind) check stays off.
    failOnModification.set(false)
    htmlOutputFile.set(layout.buildDirectory.file("reports/japicmp/japicmp.html"))

    richReport {
        addDefaultRules.set(false)
        addRule(ApiStatusBinaryCompatibilityRule::class.java)
        destinationDir.set(layout.buildDirectory.dir("reports/japicmp"))
        reportName.set("api-compatibility.html")
        title.set("${project.name}: binary compatibility with $apiBaseline")
        description.set(
            "Binary-incompatible changes. Elements annotated @API(status = INTERNAL) or " +
                "@API(status = EXPERIMENTAL) are accepted; every other change fails the build.",
        )
    }
}
