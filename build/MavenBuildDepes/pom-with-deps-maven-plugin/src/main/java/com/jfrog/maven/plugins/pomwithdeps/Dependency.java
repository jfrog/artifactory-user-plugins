package com.jfrog.maven.plugins.pomwithdeps;

import org.apache.maven.plugin.logging.Log;

/**
 * Created by user on 30/03/2016.
 */
public class Dependency {
    private String groupId;
    private String artifactId;
    private String version;

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public boolean isValid(Log log) {
        if (groupId == null) {
            log.warn("Skipping dependency: " + toString() + ". groupId cannot be null" );
            return false;
        }
        if (artifactId == null) {
            log.warn("Skipping dependency: " + toString() + ". artifactId cannot be null" );
            return false;
        }
        if (version == null) {
            log.warn("Skipping dependency: " + toString() + ". version cannot be null" );
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Dependency{" +
            "groupId='" + groupId + '\'' +
            ", artifactId='" + artifactId + '\'' +
            ", version='" + version + '\'' +
            '}';
    }
}
