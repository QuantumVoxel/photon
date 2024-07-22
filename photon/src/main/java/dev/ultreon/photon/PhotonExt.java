package dev.ultreon.photon;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;

import java.io.File;
import java.time.Instant;

public class PhotonExt {
    public String projectName = "Example Project";
    public String projectVersion = "dev";
    public String projectGroup = "com.example";
    public String projectId = "example-project";
    public Project modProject = null;
    public int javaVersion = -1;
    public boolean production = false;
    public final Instant buildDate = Instant.now();
    public File runDirectory;
    public boolean verbose = false;
    Dependency quantumVoxel;

    public Dependency getQuantumVoxel() {
        return quantumVoxel;
    }

    public String client(String version) {
        return "dev.ultreon.quantum:quantum-desktop:" + version;
    }

    public String server(String version) {
        return "dev.ultreon.quantum:quantum-server:" + version;
    }
}
