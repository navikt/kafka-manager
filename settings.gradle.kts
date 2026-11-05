pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

    repositories {
        mavenCentral()
        maven("https://packages.confluent.io/maven/")
        maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
        // Node plugin guidance for centralized repositories:
        // https://github.com/node-gradle/gradle-node-plugin/blob/7.1.0/docs/faq.md#is-this-plugin-compatible-with-centralized-repositories-declaration
        // Ivy repository layout:
        // https://docs.gradle.org/9.8.2/userguide/supported_repository_types.html#sec:ivy-repositories
        ivy {
            name = "Node.js"
            url = uri("https://nodejs.org/dist/")
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("org.nodejs", "node")
            }
        }
    }
}

rootProject.name = "kafka-manager"

include("api", "web-app")
