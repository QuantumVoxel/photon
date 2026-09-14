import java.lang.System.getenv

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

group = "net.quantumvoxel"
version = "0.1.0-SNAPSHOT"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("photon") {
            id = "net.quantumvoxel.photon"
            implementationClass = "net.quantumvoxel.photon.PhotonPlugin"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            groupId = project.group.toString()
            artifactId = project.name
        }
    }

    repositories {
        maven {
            name = "UltreonMavenReleases"
            url = uri("https://maven.ultreon.dev/releases")
            credentials {
                username = findProperty("ultreonmvn.name") as? String ?: getenv("ULTREON_MVN_NAME")
                password = findProperty("ultreonmvn.secret") as? String ?: getenv("ULTREON_MVN_SEC")
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }

        maven {
            name = "UltreonMavenSnapshots"
            url = uri("https://maven.ultreon.dev/snapshots")
            credentials {
                username = findProperty("ultreonmvn.name") as? String ?: getenv("ULTREON_MVN_NAME")
                password = findProperty("ultreonmvn.secret") as? String ?: getenv("ULTREON_MVN_SEC")
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}
