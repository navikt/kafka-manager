import com.github.gradle.node.npm.task.NpmInstallTask
import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    alias(libs.plugins.node)
}

node {
    version.set("24.22.2")
    download.set(true)
    distBaseUrl.set(null)
    npmInstallCommand.set("ci")
    nodeProjectDir.set(projectDir)
    workDir.set(layout.buildDirectory.dir("nodejs"))
    npmWorkDir.set(layout.buildDirectory.dir("npm"))
}

tasks.named<NpmInstallTask>("npmInstall") {
    args.set(listOf("--ignore-scripts", "--min-release-age=7"))
}

val testFrontend =
    tasks.register<NpmTask>("testFrontend") {
        description = "Runs the frontend tests."
        dependsOn(tasks.named("npmInstall"))
        npmCommand.set(listOf("run", "test"))
        inputs.files(fileTree("src"), file("package.json"), file("package-lock.json"), file("index.html"))
        inputs.files(file("vite.config.ts"), file("tsconfig.json"))
    }

val assembleFrontend =
    tasks.register<NpmTask>("assembleFrontend") {
        description = "Builds the frontend assets."
        dependsOn(tasks.named("npmInstall"))
        npmCommand.set(listOf("run", "build"))
        inputs.files(
            fileTree("src"),
            fileTree("public"),
            file("package.json"),
            file("package-lock.json"),
            file("index.html"),
        )
        inputs.files(file("vite.config.ts"), file("tsconfig.json"))
        outputs.dir(layout.projectDirectory.dir("dist"))
    }

tasks.named("check") {
    dependsOn(testFrontend)
}

tasks.named("assemble") {
    dependsOn(assembleFrontend)
}
