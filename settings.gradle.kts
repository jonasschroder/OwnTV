pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // A libmpv built on this PC (OwnTV_libmpv's buildscripts/wsl_build.sh), only while
        // the build command carries -Powntv.libmpvLocalRepo=<folder> (never a file) — core's :player-core then asks
        // for version "local". Core's settings carry the same lines.
        providers.gradleProperty("owntv.libmpvLocalRepo").orNull?.let { dir ->
            maven {
                name = "LocalLibmpv"
                url = uri(file(dir))
                content { includeVersion("tv.own.owntv", "libmpv", "local") }
            }
        }
        google()
        mavenCentral()
        // OwnTV's own Maven repository — public, no login: tv.own.owntv:core and :player-core
        // (built from https://github.com/ahXN00/OwnTV_Core) and tv.own.owntv:libmpv, the mpv engine
        // (https://github.com/ahXN00/OwnTV_libmpv). Served from OwnTV_Core's gh-pages branch.
        maven {
            name = "OwnTV"
            url = uri("https://ahxn00.github.io/OwnTV_Core/maven")
            content { includeGroup("tv.own.owntv") }
        }
    }
}

// Core 1.0.64 predates APIs this app uses. Build both Core modules from one immutable source
// revision until compatible artifacts are published. Prepare it with bash tools/prepare-core.sh.
// An explicit owntv.corePath still supports local Core development (and intentionally bypasses
// this pin). Gradle substitutes both tv.own.owntv dependencies by their group/project names.
val localCorePath = providers.gradleProperty("owntv.corePath").orNull?.takeIf { it.isNotBlank() }
if (localCorePath != null) {
    includeBuild(localCorePath)
} else {
    val coreCommit = file("gradle/owntv-core.commit").readText().trim()
    require(coreCommit.matches(Regex("[0-9a-f]{40}"))) { "Invalid gradle/owntv-core.commit" }
    val coreDir = file(".gradle-cache/OwnTV_Core")
    check(coreDir.resolve(".git").isDirectory) { "Run bash tools/prepare-core.sh before building." }
    val actualCommit = providers.exec {
        commandLine("git", "-C", coreDir.absolutePath, "rev-parse", "HEAD")
    }.standardOutput.asText.get().trim()
    check(actualCommit == coreCommit) { "Core revision differs from gradle/owntv-core.commit; run bash tools/prepare-core.sh." }
    val coreChanges = providers.exec {
        commandLine("git", "-C", coreDir.absolutePath, "status", "--porcelain", "--untracked-files=no")
    }.standardOutput.asText.get().trim()
    check(coreChanges.isEmpty()) { "Pinned Core has local changes; use owntv.corePath for Core development." }
    includeBuild(coreDir)
}

rootProject.name = "OwnTV"
include(":app")
// Baseline-profile generator (audit ST1). Test-only module: it ships nothing to users, it records
// the cold-start journey on a device and writes the profile :app packages.
include(":baselineprofile")

