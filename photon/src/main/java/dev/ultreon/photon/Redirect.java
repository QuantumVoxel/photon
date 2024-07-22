package dev.ultreon.photon;

import dev.ultreon.photon.prepare.IdeaPrepareKt;
import org.gradle.api.Project;

public class Redirect {
    public static void redirectIdea(Project project) {
        IdeaPrepareKt.prepareIdea(project);
    }
}
