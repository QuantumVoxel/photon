package dev.ultreon.photon


import dev.ultreon.photon.tasks.MetadataTask
import dev.ultreon.photon.tasks.PrepareRunTask
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar

@SuppressWarnings('unused')
class PhotonPlugin implements Plugin<Project> {
    static PhotonExt extension

    PhotonPlugin() {

    }


    @Override
    void apply(Project project) {
        if (project != project.rootProject) return

        project.apply(plugin: "java")
        project.apply(plugin: "java-library")
        project.apply(plugin: "idea")
        project.apply(plugin: "org.jetbrains.gradle.plugin.idea-ext")

        extension = project.extensions.create("photon", PhotonExt.class)
        extension.runDirectory = project.file("run")

        Redirect.redirectIdea(project)

        project.afterEvaluate {
            evaluateAfter(project)
        }

        project.allprojects {
            repositories.mavenCentral()
            repositories.google()
            repositories.maven { url = "https://gitlab.com/api/v4/groups/9962021/-/packages/maven" }
            repositories.maven { url = "https://maven.fabricmc.net/" }

            repositories.maven { url = "https://oss.sonatype.org/content/repositories/releases" }
            repositories.maven { url = "https://oss.sonatype.org/content/repositories/snapshots" }
            repositories.maven { url = "https://repo.glaremasters.me/repository/public/" }
            repositories.maven {
                name = "JitPack"
                url = "https://jitpack.io"
                content {
                    includeGroup("com.github.mgsx-dev.gdx-gltf")
                    includeGroup("com.github.JnCrMx")
                    includeGroup("com.github.jagrosh")
                    includeGroup("com.github.crykn.guacamole")
                    includeGroup("com.github.Ultreon")
                    includeGroup("dev.ultreon")
                    includeGroup("space.earlygrey")
                }
            }
        }

        project.configurations.create("quantum")
        project.configurations.named("quantum").configure {
            project.configurations.named("api").get().extendsFrom(it)
            project.configurations.named("implementation").get().extendsFrom(it)

            it.canBeConsumed = false
            it.canBeResolved = true
        }

        project.beforeEvaluate {
            def config = project.configurations.named("quantum").get()
            if (config.dependencies.size() == 0) {
                throw new GradleException("No Quantum Voxel dependencies found, please add one!")
            }

            if (config.dependencies.size() > 1) {
                throw new GradleException("Too many Quantum Voxel dependencies found, please add only one!")
            }

            project.dependencies.add("quantum", "dev.ultreon.quantum:desktop:" + config.dependencies.toList()[0].toString())

            extension.quantumVoxel = config.dependencies.toList()[0]
        }

        project.tasks.register("prepareRun", PrepareRunTask.class)
    }

    private static void evaluateAfter(Project project) {
        if (!extension.production)
            println("WARNING: App $extension.projectName is in developer mode.")

        project.subprojects {
            project.version = extension.projectVersion
        }

        TaskProvider<MetadataTask> metadataTask = project.tasks.register("metadata", MetadataTask.class)
        for (Project subproject : project.subprojects) {
            subproject.tasks.withType(Jar).configureEach { Jar jar ->
                metadataTask.configure {
                    it.dependsOn(jar)
                }
            }
        }
    }

    private static List<Dep> getDependencies(Configuration conf) {
        List<Dep> dependencies = []
        if (conf.isCanBeResolved()) {
            conf.getResolvedConfiguration().getResolvedArtifacts().each {
                at ->
                    def dep = at.getModuleVersion().getId()
                    dependencies.add(new Dep(dep.group, dep.name, dep.version, at.extension, at.classifier, at.file))
            }
        } else {
            throw new GradleException("Pack config can't be resolved!")
        }
        dependencies
    }


    private static String getLibraryName(Dep dep, File file, Zip zip) {
        String name
        if (dep.classifier == null || dep.classifier == "null") {
            name = dep.name + "-" + dep.version + "." + dep.extension
        } else {
            name = dep.name + "-" + dep.version + "-" + dep.classifier + "." + dep.extension
        }
        {
            def dest = "libraries/" + dep.group.replaceAll("\\.", "/") + "/" + dep.name
            if (extension.verbose)
                println "Adding \"$file.name\" to \"$dest\""

            zip.from file, new Action<CopySpec>() {
                @Override
                void execute(CopySpec spec) {
                    spec.into(dest)
                }
            }
        }
        name
    }
}
