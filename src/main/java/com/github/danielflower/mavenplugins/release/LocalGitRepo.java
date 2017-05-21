package com.github.danielflower.mavenplugins.release;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.github.danielflower.mavenplugins.release.FileUtils.pathOf;

public class LocalGitRepo {

    public final Git git;
    private final String remoteUrl;
    private boolean hasReverted = false; // A premature optimisation? In the normal case, file reverting occurs twice, which this bool prevents
    private Collection<Ref> remoteTags;

    LocalGitRepo(Git git, String remoteUrl) {
        this.git = git;
        this.remoteUrl = remoteUrl;
    }

    public void errorIfNotClean() throws ValidationException {
        Status status = currentStatus();
        boolean isClean = status.isClean();
        if (!isClean) {
            String summary = "Cannot release with uncommitted changes. Please check the following files:";
            List<String> message = new ArrayList<String>();
            message.add(summary);
            Set<String> uncommittedChanges = status.getUncommittedChanges();
            if (uncommittedChanges.size() > 0) {
                message.add("Uncommitted:");
                for (String path : uncommittedChanges) {
                    message.add(" * " + path);
                }
            }
            Set<String> untracked = status.getUntracked();
            if (untracked.size() > 0) {
                message.add("Untracked:");
                for (String path : untracked) {
                    message.add(" * " + path);
                }
            }
            message.add("Please commit or revert these changes before releasing.");
            throw new ValidationException(summary, message);
        }
    }

    private Status currentStatus() throws ValidationException {
        Status status;
        try {
            status = git.status().call();
        } catch (GitAPIException e) {
            throw new ValidationException("Error while checking if the Git repo is clean", e);
        }
        return status;
    }

    public boolean revertChanges(Log log, List<File> changedFiles) throws MojoExecutionException {
        if (hasReverted) {
            return true;
        }
        boolean hasErrors = false;
        File workTree = workingDir();
        for (File changedFile : changedFiles) {
            hasErrors = revertFile(log, changedFile, hasErrors, workTree);
        }
        hasReverted = true;
        return !hasErrors;
    }

    public boolean revertCommit(Log log, List<File> changedFiles) throws MojoExecutionException {
        if (hasReverted) {
            return true;
        }
        boolean hasErrors = false;
        File workTree = workingDir();
        for (File changedFile : changedFiles) {
            hasErrors = revertCommitedFile(log, changedFile, hasErrors, workTree);
        }
        hasReverted = true;
        return !hasErrors;
    }

    public boolean revertCommit(Log log, File changedFile) throws MojoExecutionException {
        if (hasReverted) {
            return true;
        }
        boolean hasErrors = false;
        File workTree = workingDir();
        hasErrors = revertCommitedFile(log, changedFile, hasErrors, workTree);
        hasReverted = true;
        return !hasErrors;
    }

    public boolean revertChanges(Log log, File changedFile) throws MojoExecutionException {
        if (hasReverted) {
            return true;
        }
        boolean hasErrors = false;
        File workTree = workingDir();
        hasErrors = revertFile(log, changedFile, hasErrors, workTree);
        hasReverted = true;
        return !hasErrors;
    }

    private boolean revertCommitedFile(Log log, File changedFile, boolean hasErrors, File workTree) {
        try {
            String pathRelativeToWorkingTree = Repository.stripWorkDir(workTree, changedFile);
            git.reset().setRef("HEAD~1").setMode(ResetCommand.ResetType.HARD).addPath(pathRelativeToWorkingTree).call();
        } catch (Exception e) {
            hasErrors = true;
            log.error("Unable to revert changes to " + changedFile + " - you may need to manually revert this file. Error was: " + e.getMessage());
        }
        return hasErrors;
    }

    private boolean revertFile(Log log, File changedFile, boolean hasErrors, File workTree) {
        try {
            String pathRelativeToWorkingTree = Repository.stripWorkDir(workTree, changedFile);
            git.checkout().addPath(pathRelativeToWorkingTree).call();
        } catch (Exception e) {
            hasErrors = true;
            log.error("Unable to revert changes to " + changedFile + " - you may need to manually revert this file. Error was: " + e.getMessage());
        }
        return hasErrors;
    }

    private File workingDir() throws MojoExecutionException {
        try {
            return git.getRepository().getWorkTree().getCanonicalFile();
        } catch (IOException e) {
            throw new MojoExecutionException("Could not locate the working directory of the Git repo", e);
        }
    }

    public boolean hasLocalTag(String tagName) throws GitAPIException {
        return GitHelper.hasLocalTag(git, tagName);
    }

    public void commitChanges() throws GitAPIException {
        git.commit().setAll(true).setMessage("Incremented versions").call();
    }

    public void tagRepoAndPush(AnnotatedTag tag) throws GitAPIException {
        Ref tagRef = tagRepo(tag);
        pushTag(tagRef);
    }

    private void pushTag(Ref tagRef) throws GitAPIException {
        PushCommand pushCommand = git.push().add(tagRef);
        if (remoteUrl != null) {
            pushCommand.setRemote(remoteUrl);
        }
        pushCommand.call();
    }

    public Ref tagRepo(AnnotatedTag tag) throws GitAPIException {
        Ref tagRef = tag.saveAtHEAD(git);
        return tagRef;
    }

    public static LocalGitRepo fromModule(ReleasableModule module) throws ValidationException {
        return LocalGitRepo.fromDir(module.getRelativePathToModule(),
            ReleaseMojo.getRemoteUrlOrNullIfNoneSet(module.getProject().getOriginalModel().getScm(), module.getProject().getModel().getScm()));
    }

    /**
     * Uses the current working dir to open the Git repository.
     *
     * @param remoteUrl The value in pom.scm.connection or null if none specified, in which case the default remote is used.
     * @throws ValidationException if anything goes wrong
     */
    public static LocalGitRepo fromDir(String directory, String remoteUrl) throws ValidationException {
        Git git;
        File gitDir = new File(directory);
        try {
            git = Git.open(gitDir);
        } catch (RepositoryNotFoundException rnfe) {
            String fullPathOfCurrentDir = pathOf(gitDir);
            File gitRoot = getGitRootIfItExistsInOneOfTheParentDirectories(new File(fullPathOfCurrentDir));
            String summary;
            List<String> messages = new ArrayList<String>();
            if (gitRoot == null) {
                summary = "Releases can only be performed from Git repositories.";
                messages.add(summary);
                messages.add(fullPathOfCurrentDir + " is not a Git repository.");
            } else {
                summary = "The release plugin can only be run from the root folder of your Git repository";
                messages.add(summary);
                messages.add(fullPathOfCurrentDir + " is not the root of a Gir repository");
                messages.add("Try running the release plugin from " + pathOf(gitRoot));
            }
            throw new ValidationException(summary, messages);
        } catch (Exception e) {
            throw new ValidationException("Could not open git repository. Is " + pathOf(gitDir) + " a git repository?", Arrays.asList("Exception returned when accessing the git repo:", e.toString()));
        }
        return new LocalGitRepo(git, remoteUrl);
    }

    /**
     * Uses the current working dir to open the Git repository.
     *
     * @param remoteUrl The value in pom.scm.connection or null if none specified, in which case the default remote is used.
     * @throws ValidationException if anything goes wrong
     */
    public static LocalGitRepo fromCurrentDir(String remoteUrl) throws ValidationException {
        return fromDir(".", remoteUrl);
    }

    private static File getGitRootIfItExistsInOneOfTheParentDirectories(File candidateDir) {
        while (candidateDir != null && /* HACK ATTACK! Maybe.... */ !candidateDir.getName().equals("target")) {
            if (new File(candidateDir, ".git").isDirectory()) {
                return candidateDir;
            }
            candidateDir = candidateDir.getParentFile();
        }
        return null;
    }

    public List<String> remoteTagsFrom(List<AnnotatedTag> annotatedTags) throws GitAPIException {
        List<String> tagNames = new ArrayList<String>();
        for (AnnotatedTag annotatedTag : annotatedTags) {
            tagNames.add(annotatedTag.name());
        }
        return getRemoteTags(tagNames);
    }

    public List<String> getRemoteTags(List<String> tagNamesToSearchFor) throws GitAPIException {
        List<String> results = new ArrayList<String>();
        Collection<Ref> remoteTags = allRemoteTags();
        for (Ref remoteTag : remoteTags) {
            for (String proposedTag : tagNamesToSearchFor) {
                if (remoteTag.getName().equals("refs/tags/" + proposedTag)) {
                    results.add(proposedTag);
                }
            }
        }
        return results;
    }

    public boolean tagExists(AnnotatedTag tagToSearchFor) throws GitAPIException {
        return tagExists(tagToSearchFor.name());
    }

    public boolean tagExists(String tagNameToSearchFor) throws GitAPIException {
        Collection<Ref> remoteTags = allRemoteTags();
        for (Ref remoteTag : remoteTags) {
            if (remoteTag.getName().equals("refs/tags/" + tagNameToSearchFor)) {
                return true;
            }
        }
        return false;
    }

    public Collection<Ref> allRemoteTags() throws GitAPIException {
        if (remoteTags == null) {
            LsRemoteCommand lsRemoteCommand = git.lsRemote().setTags(true).setHeads(false);
            if (remoteUrl != null) {
                lsRemoteCommand.setRemote(remoteUrl);
            }
            remoteTags = lsRemoteCommand.call();
        }
        return remoteTags;
    }
}
