plugins {
    base
}

description = "Java SDK for the scribble.pub Bot API"

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    repositories {
        mavenCentral()
    }

    // Java 17, not the JDK this happens to be built with: the SDK is meant to drop into any
    // Spring Boot 3/4 or plain-Java service, and Boot 4's own floor is 17. `release` compiles
    // against the 17 API even on a newer JDK, so nothing newer can leak in by accident.
    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
        withJavadocJar()
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }
    tasks.withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            // Public API is documented, but a missing @param on a record component should not
            // fail a release build.
            addStringOption("Xdoclint:none", "-quiet")
        }
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    extensions.configure<PublishingExtension> {
        publications.create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set(project.name)
                // Lazy: `subprojects {}` runs before the module build scripts that set
                // `description`, and Maven Central rejects a POM without one.
                description.set(provider { project.description })
                url.set(providers.gradleProperty("pomUrl"))
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set(providers.gradleProperty("pomDeveloperId"))
                        name.set(providers.gradleProperty("pomDeveloperName"))
                    }
                }
                scm {
                    connection.set(providers.gradleProperty("pomScmConnection"))
                    developerConnection.set(providers.gradleProperty("pomScmDeveloperConnection"))
                    url.set(providers.gradleProperty("pomUrl"))
                }
            }
        }
        repositories {
            // Where a Maven Central release is assembled before it is zipped into a Publisher
            // API bundle (see the `centralBundle` task below). Not a real remote.
            maven {
                name = "staging"
                url = rootProject.layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()
            }
            // Central Portal's snapshot repository, where the upstream-sync branches publish so a
            // port can be tried in a consumer before it is merged. Nothing like a release: a plain
            // Maven deploy, no bundle, no signatures, overwritten in place, dropped after 90 days.
            maven {
                name = "centralSnapshots"
                url = uri("https://central.sonatype.com/repository/maven-snapshots/")
                credentials {
                    username = providers.gradleProperty("centralUsername")
                        .orElse(providers.environmentVariable("CENTRAL_USERNAME")).orNull
                    password = providers.gradleProperty("centralPassword")
                        .orElse(providers.environmentVariable("CENTRAL_PASSWORD")).orNull
                }
            }
            maven {
                name = "githubPackages"
                url = uri("https://maven.pkg.github.com/hteariH/scribble-bot-sdk-java")
                credentials {
                    username = providers.gradleProperty("gpr.user")
                        .orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                    password = providers.gradleProperty("gpr.key")
                        .orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
                }
            }
        }
    }

    extensions.configure<SigningExtension> {
        // Signing is required by Maven Central and pointless everywhere else, so it only turns
        // on when a key is actually supplied (ORG_GRADLE_PROJECT_signingInMemoryKey).
        val key = providers.gradleProperty("signingInMemoryKey").orNull
        val password = providers.gradleProperty("signingInMemoryKeyPassword").orNull
        isRequired = key != null
        if (key != null) {
            useInMemoryPgpKeys(key, password)
            sign(extensions.getByType<PublishingExtension>().publications["maven"])
        }
    }
}

// Maven Central's Publisher API takes one zip of a Maven-layout repository. Build it with:
//   ./gradlew centralBundle -PsigningInMemoryKey=... -PsigningInMemoryKeyPassword=...
// then upload build/central-bundle.zip (the publish workflow does both).
val centralBundle by tasks.registering(Zip::class) {
    group = "publishing"
    description = "Packages every module into a Maven Central Publisher API bundle."
    dependsOn(subprojects.map { "${it.path}:publishAllPublicationsToStagingRepository" })
    from(layout.buildDirectory.dir("staging-deploy"))
    destinationDirectory.set(layout.buildDirectory)
    archiveFileName.set("central-bundle.zip")
}
