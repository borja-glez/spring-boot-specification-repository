plugins {
    java
    id("specification-java-base-conventions")
}

val developmentOnly: Configuration by configurations.creating
configurations.runtimeClasspath { extendsFrom(developmentOnly) }
