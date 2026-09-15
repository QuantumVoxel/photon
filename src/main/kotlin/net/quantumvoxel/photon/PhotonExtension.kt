package net.quantumvoxel.photon

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.language.jvm.tasks.ProcessResources

abstract class PhotonExtension {
    abstract val gameVersion: org.gradle.api.provider.Property<String>
    abstract val javaVersion: org.gradle.api.provider.Property<Int>
    abstract val modMenu: org.gradle.api.provider.Property<Boolean>
    abstract val splitClientSources: org.gradle.api.provider.Property<Boolean>
    abstract val generateSources: org.gradle.api.provider.Property<Boolean>
    abstract val generateJavadoc: org.gradle.api.provider.Property<Boolean>
}

class PhotonPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply("java-library")

        val photon = project.extensions.create<PhotonExtension>("photon")

        photon.javaVersion.convention(25)
        photon.gameVersion.convention("")
        photon.modMenu.convention(false)
        photon.splitClientSources.convention(false)
        photon.generateSources.convention(false)
        photon.generateJavadoc.convention(false)

        configureRepositories(project)
        project.afterEvaluate {
            configureJava(project, photon)
            configureSourceSets(project, photon)
            configureDependencies(project, photon)
            configureResources(project, photon)
            configureJar(project, photon)
            configureRun(project, photon)
            configureRunDirectory(project)
        }
    }

    private fun configureRepositories(project: Project) {
        project.repositories.mavenCentral()

        project.repositories.maven {
            name = "UltreonReleases"
            url = project.uri("https://maven.ultreon.dev/releases")
        }

        project.repositories.maven {
            name = "UltreonSnapshots"
            url = project.uri("https://maven.ultreon.dev/snapshots")
        }

        project.repositories.mavenLocal()

        project.repositories.maven {
            name = "Jitpack"
            url = project.uri("https://jitpack.io")
        }
    }

    private fun configureJava(project: Project, photon: PhotonExtension) {
        val java = project.extensions.getByType<JavaPluginExtension>()

        java.toolchain {
            languageVersion.set(
                org.gradle.jvm.toolchain.JavaLanguageVersion.of(photon.javaVersion.get())
            )
        }

        if (photon.generateSources.get()) {
            java.withSourcesJar()
        }
        if (photon.generateJavadoc.get()) {
            java.withJavadocJar()
        }
    }

    private fun configureSourceSets(project: Project, photon: PhotonExtension) {
        val java = project.extensions.getByType<JavaPluginExtension>()

        if (photon.splitClientSources.get()) {
            val main = java.sourceSets.getByName(
                SourceSet.MAIN_SOURCE_SET_NAME
            )

            val client = java.sourceSets.create("client")

            client.compileClasspath += main.output
            client.runtimeClasspath += main.output
        }
    }

    private fun configureDependencies(
        project: Project,
        photon: PhotonExtension
    ) {
        val clientImplementation =
            if (photon.splitClientSources.get()) {
                project.configurations.getByName("clientImplementation")
            } else {
                project.configurations.getByName("implementation")
            }

        val implementation =
            project.configurations.getByName("implementation")

        if (photon.splitClientSources.get())
            clientImplementation.extendsFrom(implementation)


        clientImplementation.dependencies.add(
            project.dependencies.create(
                "net.quantumvoxel:client:" +
                        photon.gameVersion.get()
            )
        )

        clientImplementation.dependencies.add(
            project.dependencies.create(
                "net.quantumvoxel:desktop:" +
                        photon.gameVersion.get()
            )
        )

        if (photon.modMenu.get()) {
            clientImplementation.dependencies.add(
                project.dependencies.create(
                    "net.quantumvoxel:modmenu:" +
                            photon.gameVersion.get()
                )
            )
        }

        implementation.dependencies.add(
            project.dependencies.create(
                "net.quantumvoxel:server:" +
                        photon.gameVersion.get()
            )
        )

        implementation.dependencies.add(
            project.dependencies.create(
                "net.quantumvoxel:gameprovider:" +
                        photon.gameVersion.get()
            )
        )
    }

    private fun configureResources(
        project: Project,
        photon: PhotonExtension
    ) {
        project.tasks.getByName<ProcessResources>("processResources") {
            inputs.property(
                "version",
                project.version
            )

            inputs.property(
                "quantumvoxel_version",
                photon.gameVersion
            )

            filesMatching("fabric.mod.json") {
                expand(
                    mapOf(
                        "version" to project.version,
                        "quantumvoxel_version" to photon.gameVersion.get()
                    )
                )
            }
        }
    }

    private fun configureJar(project: Project, photon: PhotonExtension) {
        project.tasks.named<Jar>("jar") {
            val java = project.extensions.getByType<JavaPluginExtension>()

            if (photon.splitClientSources.get()) {
                from(
                    java.sourceSets
                        .getByName("client")
                        .output
                )
            }
        }
    }

    private fun configureRun(project: Project, photon: PhotonExtension) {
        project.tasks.register<JavaExec>("runClient") {
            dependsOn(project.tasks.getByName("jar"))

            group = "photon"

            outputs.upToDateWhen { false }

            val java = project.extensions.getByType<JavaPluginExtension>()

            val classpath = if (photon.splitClientSources.get()) {
                java.sourceSets.getByName("client").runtimeClasspath
            } else {
                java.sourceSets.getByName("main").runtimeClasspath
            }

            classpath(classpath.files)
            mainClass.set("net.fabricmc.loader.impl.launch.knot.KnotClient")
            if (System.getProperty("os.name").lowercase().contains("mac")) {
                jvmArgs(
                    "-Dfabric.addMods=${project.tasks.named<Jar>("jar").get().archiveFile.get().asFile.absolutePath}",
                    "-XstartOnFirstThread",
                    "-Dquantum.gameDir=${
                        project.file("run/client/main")
                    }",
                    "-Xmx6g"
                )
            } else {
                jvmArgs(
                    "-Dfabric.addMods=${
                        project.tasks.named<Jar>("jar").get().archiveFile.get().asFile.absolutePath
                    }",
                    "-Dquantum.gameDir=${
                        project.file("run/client/main")
                    }",
                    "-Xmx6g"
                )
            }

            workingDir = project.file("run/client/main")
        }
        project.tasks.register<JavaExec>("runClientAlt") {
            dependsOn(project.tasks.getByName("jar"))

            group = "photon"

            outputs.upToDateWhen { false }

            val java = project.extensions.getByType<JavaPluginExtension>()

            val classpath = if (photon.splitClientSources.get()) {
                java.sourceSets.getByName("client").runtimeClasspath
            } else {
                java.sourceSets.getByName("main").runtimeClasspath
            }

            classpath(classpath.files)
            mainClass.set("net.fabricmc.loader.impl.launch.knot.KnotClient")
            if (System.getProperty("os.name").lowercase().contains("mac")) {
                jvmArgs(
                    "-Dfabric.addMods=${project.tasks.named<Jar>("jar").get().archiveFile.get().asFile.absolutePath}",
                    "-XstartOnFirstThread",
                    "-Dquantum.gameDir=${
                        project.file("run/client/alt")
                    }",
                    "-Xmx6g"
                )
            } else {
                jvmArgs(
                    "-Dfabric.addMods=${
                        project.tasks.named<Jar>("jar").get().archiveFile.get().asFile.absolutePath
                    }",
                    "-Dquantum.gameDir=${
                        project.file("run/client/alt")
                    }",
                    "-Xmx6g"
                )
            }

            workingDir = project.file("run/client/alt")
        }
        project.tasks.register<JavaExec>("runServer") {
            dependsOn(project.tasks.getByName("jar"))

            group = "photon"

            outputs.upToDateWhen { false }

            val java = project.extensions.getByType<JavaPluginExtension>()

            val classpath = java.sourceSets.getByName("main").runtimeClasspath

            classpath(classpath.files)
            mainClass.set("net.fabricmc.loader.impl.launch.knot.KnotServer")
            jvmArgs(
                "-Dfabric.addMods=${
                    project.tasks.named<Jar>("jar").get().archiveFile.get().asFile.absolutePath
                }",
                "-Dquantum.gameDir=${
                    project.file("run/server")
                }",
                "-Xmx6g"
            )

            workingDir = project.file("run/server")
        }
    }

    private fun configureRunDirectory(project: Project) {
        project.layout.projectDirectory
            .dir("run/client/main")
            .asFile
            .mkdirs()

        project.layout.projectDirectory
            .dir("run/client/alt")
            .asFile
            .mkdirs()

        project.layout.projectDirectory
            .dir("run/server")
            .asFile
            .mkdirs()
    }
}