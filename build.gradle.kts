// Copyright Ion Fusion contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

plugins {
    java
    application
    jacoco
}

version = "R37_dev"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.amazon.ion:ion-java:1.11.4")
    // TODO This shouldn't be needed when consumers embed Fusion.
    //  It's a build-time dependency, but here b/c consumers use the CLI to
    //  generate their docs.  That should be handled by a plugin instead.
    implementation("org.markdownj:markdownj:0.3.0-1.0.2b4")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation("org.hamcrest:hamcrest:3.0")
    testImplementation("org.htmlunit:htmlunit:4.6.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }

    withJavadocJar()
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src"))
        }
        // We currently commingle non-Java resources like kernel_docs.ion
        // I'm not sure whether the fusion tree should be here; WRT the jar
        // those are resources to be included.
        resources {
            setSrcDirs(listOf("src"))
            exclude("**/*.html")   // overview.html, in particular
        }
    }
    test {
        java {
            setSrcDirs(listOf("tst"))
        }
    }
}

// Default output paths, hard-coded because the DSL doesn't seem to expose them.
val docsDir    = layout.buildDirectory.dir("docs")
val libsDir    = layout.buildDirectory.dir("libs")
val reportsDir = layout.buildDirectory.dir("reports")

// New output paths for Fusion code coverage.
val fcovDataDir = layout.buildDirectory.dir("fcov")
val fcovReportDir = reportsDir.get().dir("fcov")


// Various resources refer to the current version label.
tasks.processResources {
    // Embed our version in the jar so the CLI can print it.
    expand("project_version" to project.version.toString())
}


// Bundle the Fusion bootstrap repository in our jar.
tasks.jar {
    // It might be better if these were modeled as resources in the main
    // sourceSet, so they are automatically added to the classpath when testing.
    // It's unclear how to change the parent directory that way.
    into("FUSION-REPO") {
        from(layout.projectDirectory.dir("fusion"))
        exclude("**/*.md")
        includeEmptyDirs = true
    }
}


//=============================================================================
// Tests

tasks.test {
    // com.amazon.fusion.DocumentationTest checks the generated documentation.
    dependsOn(fusiondoc)

    // com.amazon.fusion.ClassLoaderModuleRepositoryTest uses ftst-repo.jar.
    dependsOn(ftstRepo)

    useJUnitPlatform()

    // Collect Fusion coverage data IFF a report is being generated.
    mustRunAfter(fcovConfigure)
    jvmArgumentProviders.add {
        if (fcovRunning) {
            logger.lifecycle("Enabling Fusion code coverage instrumentation")
            listOf("-Dcom.amazon.fusion.coverage.DataDir=" + fcovDataDir.get().asFile.path)
        }
        else {
            listOf()
        }
    }
}

val ftstRepo = tasks.register<Jar>("ftstRepo") {
    destinationDirectory = libsDir
    archiveFileName = "ftst-repo.jar"

    into("FUSION-REPO") {
        from(layout.projectDirectory.dir("ftst/repo"))
        includeEmptyDirs = true
    }
}


// We rely on Gradle defaults to invoke unit tests in the `test` suite.
// Here, we define a separate test suite for checking the content of our
// distribution archives. This is effectively testing our Gradle build logic.

// This approach is incubating; expect changes.
// https://docs.gradle.org/current/userguide/jvm_test_suite_plugin.html

testing {
    suites {
        // This test suite ensures the distribution is functional.
        register<JvmTestSuite>("distTest") {
            sources {
                java {
                    setSrcDirs(listOf("distTest"))
                }
            }

            // "Future iterations of the plugin will allow defining multiple
            // targets based other attributes, such as a particular JDK runtime."
            targets {
                all {
                    testTask.configure {
                        // Install the tarball so we can test it.
                        dependsOn(tasks.installDist)
                        // If unit tests are running, complete them first.
                        shouldRunAfter(tasks.test)
                    }
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("distTest"))
}


//=============================================================================
// Code Coverage

// https://docs.gradle.org/current/userguide/jacoco_plugin.html

jacoco {
    // Do this to keep JaCoCo version stable when updating Gradle.
    // As of Gradle 8.10, the default is:
//    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    // We don't dependOn distTest; that tests the packaging not the product.
    dependsOn(tasks.test)
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}


// TODO Clean fcovDataDir somewhere

val fcovConfigure = tasks.register<WriteProperties>("fcovConfigure") {
    destinationFile = fcovDataDir.get().file("config.properties")

    property("IncludedModules", "/fusion")

    // Enable this if you want to check that tests are being run.
    // TODO Figure out why this causes report generation to fail.
//    property("IncludedSources", "ftst")
}

// Name is ick but mirrors jacocoTestReport
val fcovTestReport = tasks.register<JavaExec>("fcovTestReport") {
    dependsOn(fcovConfigure, tasks.test)

    group = "verification"
    description = "Generates Fusion code coverage report"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.amazon.fusion.cli.Cli"
    args = listOf("report_coverage",
                  fcovDataDir.get().asFile.path,
                  fcovReportDir.asFile.path)

    enableAssertions = true
}


// Signal to test task to collect coverage data.
var fcovRunning = false
gradle.taskGraph.whenReady {
    fcovRunning = hasTask(fcovTestReport.get())
}


//=============================================================================
// Documentation

tasks.javadoc {
    exclude("**/_Private_*",
            "com/amazon/fusion/cli",
            "com/amazon/fusion/util/function/**",
            "com/amazon/fusion/util/hamt/**")

    title = "FusionJava API Reference"

    options {
        // https://github.com/gradle/gradle/issues/7038
        this as StandardJavadocDocletOptions

        docEncoding = "UTF-8"
        overview = "$projectDir/src/com/amazon/fusion/overview.html"

        header = "FusionJava API Reference"
        bottom = "<center>Copyright &#169; 2012-2024 " +
                "Amazon.com. All Rights Reserved.</center>"
    }
}

val fusiondocDir = docsDir.get().dir("fusiondoc")

tasks.register<JavaExec>("fusiondocGen") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.amazon.fusion.cli.Cli"
    args = listOf("document", fusiondocDir.asFile.path, "fusion")

    enableAssertions = true

    outputs.dir(fusiondocDir)
}

val fusiondoc by tasks.register<Copy>("fusiondoc") {
    group = "Documentation"
    description = "Generates Fusion language and library documentation."

    dependsOn("fusiondocGen")

    // Copy Favicon and CSS from `doc` directory.
    into(fusiondocDir)
    from(layout.projectDirectory.dir("doc"))
    exclude("**/*.md")
    includeEmptyDirs = false
}


//=============================================================================
// Distribution

// https://docs.gradle.org/current/userguide/application_plugin.html#sec:the_distribution
distributions {
    main {
        contents {
            tasks.distTar {
                dependsOn("fusiondoc", "javadoc")
            }
            tasks.distZip {
                dependsOn("fusiondoc", "javadoc")
            }
            tasks.installDist {
                dependsOn("fusiondoc", "javadoc")
            }

            into("docs") {
                from(docsDir)
            }
        }
    }
}


tasks {
    // It took a loooong time to figure out how to access APP_HOME.
    // https://discuss.gradle.org/t/42870/4
    // https://stackoverflow.com/questions/22153041?rq=3
    // It's still not entirely working: `gradle run` doesn't see the replaced script.
    startScripts {
        doLast {
            unixScript.writeText(unixScript.readText().replace("{{APP_HOME}}", "'\${APP_HOME}'"))
            windowsScript.writeText(windowsScript.readText().replace("{{APP_HOME}}", "%APP_HOME%"))
        }
    }
}


application {
    applicationName = "fusion"
    mainClass.set("com.amazon.fusion.cli.Cli")
    // This is unused today, but is likely to be useful and it was hard to do.
    applicationDefaultJvmArgs = listOf("-Dcom.amazon.fusion.Home={{APP_HOME}}/fusion")
}


// Gradle doesn't seem to have an equivalent "do everything" task.
tasks.register("release") {
    group = "Build"
    description = "Build all artifacts and reports"

    dependsOn(tasks.build, tasks.jacocoTestReport, fcovTestReport)
}
