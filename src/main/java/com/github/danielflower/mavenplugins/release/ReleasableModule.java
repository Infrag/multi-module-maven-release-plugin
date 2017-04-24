package com.github.danielflower.mavenplugins.release;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

public class ReleasableModule {

    private final MavenProject project;
    private final VersionName version;
    private final String tagName;
    private final String equivalentVersion;
    private final String relativePathToModule;

    private File changedPom;
    private LocalGitRepo repo;
    private List<String> errors;
    private AnnotatedTag annotatedTag;


    public ReleasableModule(MavenProject project, VersionName version, String equivalentVersion, String relativePathToModule, LocalGitRepo repo) {
        this.project = project;
        this.version = version;
        this.equivalentVersion = equivalentVersion;
        this.relativePathToModule = relativePathToModule;
        this.tagName = project.getArtifactId() + "-" + version.releaseVersion();
        this.repo = repo;
    }

    public AnnotatedTag figureOutTagNamesAndThrowIfAlreadyExists(Log log, List<String> modulesToRelease)
        throws GitAPIException, ValidationException {
        annotatedTag = null;
        if (!willBeReleased()) {
            log.info("No need to release the module, skipping...");
            return null;
        }
        if (modulesToRelease == null || modulesToRelease.size() == 0 || isOneOf(modulesToRelease)) {
            String tag = getTagName();
            if (getRepo().hasLocalTag(tag)) {
                String summary = "There is already a tag named " + tag + " in this repository.";
                throw new ValidationException(summary, asList(
                    summary,
                    "It is likely that this version has been released before.",
                    "Please try incrementing the build number and trying again."
                ));
            }

            annotatedTag = AnnotatedTag.create(tag, getVersion(), getBuildNumber());
        }
        if (annotatedTag != null && getRepo().tagExists(annotatedTag)) {
            String summary = "Cannot release because there is already a tag with the same build number on the remote Git repo.";
            List<String> messages = new ArrayList<String>();
            messages.add(summary);
            messages.add(" * There is already a tag named " + annotatedTag.name() + " in the remote repo.");
            messages.add("Please try releasing again with a new build number.");
            throw new ValidationException(summary, messages);
        }
        return annotatedTag;
    }

    public void tagAndPushRepo(Log log, boolean pushTags) throws GitAPIException {
        log.info("About to tag the repository with " + annotatedTag.name());
        repo.commitChanges();
        if (pushTags) {
            repo.tagRepoAndPush(annotatedTag);
        } else {
            repo.tagRepo(annotatedTag);
        }
    }


    public String getTagName() {
        return tagName;
    }

    public String getNewVersion() {
        return version.releaseVersion();
    }

    public String getArtifactId() {
        return project.getArtifactId();
    }

    public String getGroupId() {
        return project.getGroupId();
    }

    public MavenProject getProject() {
        return project;
    }

    public String getVersion() {
        return version.businessVersion();
    }

    public long getBuildNumber() {
        return version.buildNumber();
    }

    public boolean isOneOf(List<String> moduleNames) {
        String modulePath = project.getBasedir().getName();
        for (String moduleName : moduleNames) {
            if (modulePath.equals(moduleName)) {
                return true;
            }
        }
        return false;
    }

    public boolean willBeReleased() {
        return equivalentVersion == null;
    }

    public String getVersionToDependOn() {
        return willBeReleased() ? version.releaseVersion() : equivalentVersion;
    }

    public String getRelativePathToModule() {
        return relativePathToModule;
    }

    public ReleasableModule createReleasableVersion() {
        return new ReleasableModule(project, version, null, relativePathToModule, repo);
    }

    public LocalGitRepo getRepo() {
        return repo;
    }

    public void setRepo(LocalGitRepo repo) {
        this.repo = repo;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public File getChangedPom() {
        return changedPom;
    }

    public void setChangedPom(File changedPom) {
        this.changedPom = changedPom;
    }

    public boolean revertChanges(Log log) throws MojoExecutionException {
        return getRepo().revertChanges(log, getChangedPom());
    }
}
