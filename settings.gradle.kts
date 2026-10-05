pluginManagement {
    val localConventions = file("../gradle-conventions")
    val hasPackageCredentials =
        listOf("GITHUB_ACTOR", "GITHUB_TOKEN").all {
            providers.environmentVariable(it).orNull?.isNotBlank() == true
        }

    if (!hasPackageCredentials && localConventions.resolve("settings.gradle.kts").isFile) {
        includeBuild(localConventions)
    }

    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/jjt-ingsis/gradle-conventions")

            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }

        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "permissions-service"
