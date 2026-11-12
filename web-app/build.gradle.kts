import com.github.gradle.node.pnpm.task.PnpmInstallTask
import com.github.gradle.node.pnpm.task.PnpmTask

plugins {
    base
    alias(libs.plugins.node)
}

node {
    version.set("24.22.2")
    pnpmVersion.set("12.15.0")
    download.set(true)
    distBaseUrl.set(null)
    nodeProjectDir.set(projectDir)
    workDir.set(layout.buildDirectory.dir("nodejs"))
    pnpmWorkDir.set(layout.buildDirectory.dir("pnpm"))
}

tasks.named<PnpmInstallTask>("pnpmInstall") {
    args.set(listOf("--frozen-lockfile", "--ignore-scripts"))
    inputs.files(file("pnpm-workspace.yaml"), file(".npmrc"))
}

val testFrontend =
    tasks.register<PnpmTask>("testFrontend") {
        description = "Runs the frontend tests."
        dependsOn(tasks.named("pnpmInstall"))
        pnpmCommand.set(listOf("run", "test"))
        inputs.files(
            fileTree("src"),
            file("package.json"),
            file("pnpm-lock.yaml"),
            file("pnpm-workspace.yaml"),
            file(".npmrc"),
            file("index.html"),
        )
        inputs.files(file("vite.config.mts"), file("tsconfig.json"))
    }

val lintFrontend =
    tasks.register<PnpmTask>("lintFrontend") {
        description = "Lints the frontend sources."
        dependsOn(tasks.named("pnpmInstall"))
        pnpmCommand.set(listOf("run", "lint"))
        inputs.files(
            fileTree("src"),
            file("package.json"),
            file("pnpm-lock.yaml"),
            file("pnpm-workspace.yaml"),
            file(".npmrc"),
            file("eslint.config.mjs"),
        )
        inputs.files(file("tsconfig.json"))
    }

val assembleFrontend =
    tasks.register<PnpmTask>("assembleFrontend") {
        description = "Builds the frontend assets."
        dependsOn(tasks.named("pnpmInstall"))
        pnpmCommand.set(listOf("run", "build"))
        inputs.files(
            fileTree("src"),
            fileTree("public"),
            file("package.json"),
            file("pnpm-lock.yaml"),
            file("pnpm-workspace.yaml"),
            file(".npmrc"),
            file("index.html"),
        )
        inputs.files(file("vite.config.mts"), file("tsconfig.json"))
        inputs.property("viteAppVersion", providers.environmentVariable("VITE_APP_VERSION").orElse(""))
        outputs.dir(layout.projectDirectory.dir("dist"))
    }

tasks.named("check") {
    dependsOn(testFrontend, lintFrontend)
}

tasks.named("assemble") {
    dependsOn(assembleFrontend)
}
