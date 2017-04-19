package com.github.danielflower.mavenplugins.release;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.WriterFactory;

import java.io.File;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class PomUpdater {

    private final Log log;
    private final Reactor reactor;

    public PomUpdater(Log log, Reactor reactor) {
        this.log = log;
        this.reactor = reactor;
    }

    public UpdateResult updateVersion() throws MojoExecutionException, ValidationException {
        List<ReleasableModule> alteredModules = new ArrayList<>();
        for (ReleasableModule module : reactor.getModulesInBuildOrder()) {
            try {
                MavenProject project = module.getProject();
                if (module.willBeReleased()) {
                    log.info("Going to release " + module.getArtifactId() + " " + module.getNewVersion());
                }

                List<String> errorsForCurrentPom = alterModel(project, module.getNewVersion());
                module.setErrors(errorsForCurrentPom);

                File pom = project.getFile().getCanonicalFile();
                module.setChangedPom(pom);
                alteredModules.add(module);

                Writer fileWriter = WriterFactory.newXmlWriter(pom);

                Model originalModel = project.getOriginalModel();
                try {
                    MavenXpp3Writer pomWriter = new MavenXpp3Writer();
                    pomWriter.write(fileWriter, originalModel);
                } finally {
                    fileWriter.close();
                }
            } catch (Exception e) {
                return new UpdateResult(log, alteredModules, e);
            }
        }
        return new UpdateResult(log, alteredModules, null);
    }

    public static class UpdateResult {
        public final List<ReleasableModule> alteredModules;
        public final List<String> moduleErrors = new ArrayList<>();
        public final Exception unexpectedException;
        public final Log log;

        public UpdateResult(Log log, List<ReleasableModule> alteredModules, Exception unexpectedException) throws ValidationException, MojoExecutionException {
            this.alteredModules = alteredModules;
            for (ReleasableModule module : alteredModules) {
                if (module.getErrors() != null) {
                    moduleErrors.addAll(module.getErrors());
                }
            }
            this.unexpectedException = unexpectedException;
            this.log = log;
        }

        public boolean success() {
            return (moduleErrors == null || moduleErrors.isEmpty()) && (unexpectedException == null);
        }

        public void revertChanges(Log log) throws MojoExecutionException, ValidationException {
            if (!success()) {
                log.info("Going to revert changes because there was an error.");
                for (ReleasableModule module : alteredModules) {
                    module.revertChanges(log);
                }
                if (unexpectedException != null) {
                    throw new ValidationException("Unexpected exception while setting the release versions in the pom", unexpectedException);
                } else {
                    String summary = "Cannot release with references to snapshot dependencies";
                    List<String> messages = new ArrayList<String>();
                    messages.add(summary);
                    messages.add("The following dependency errors were found:");
                    for (String dependencyError : moduleErrors) {
                        messages.add(" * " + dependencyError);
                    }
                    throw new ValidationException(summary, messages);
                }
            }
        }
    }

    private List<String> alterModel(MavenProject project, String newVersion) {
        Model originalModel = project.getOriginalModel();
        originalModel.setVersion(newVersion);

        List<String> errors = new ArrayList<String>();

        String searchingFrom = project.getArtifactId();
        MavenProject parent = project.getParent();
        if (parent != null && isSnapshot(parent.getVersion())) {
            try {
                ReleasableModule parentBeingReleased = reactor.find(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
                originalModel.getParent().setVersion(parentBeingReleased.getVersionToDependOn());
                log.debug(" Parent " + parentBeingReleased.getArtifactId() + " rewritten to version " + parentBeingReleased.getVersionToDependOn());
            } catch (UnresolvedSnapshotDependencyException e) {
                errors.add("The parent of " + searchingFrom + " is " + e.artifactId + " " + e.version);
            }
        }

        Properties projectProperties = project.getProperties();
        for (Dependency dependency : originalModel.getDependencies()) {
            String version = dependency.getVersion();
            if (isSnapshot(resolveVersion(version, projectProperties))) {
                try {
                    ReleasableModule dependencyBeingReleased = reactor.find(dependency.getGroupId(), dependency.getArtifactId(), version);
                    dependency.setVersion(dependencyBeingReleased.getVersionToDependOn());
                    log.debug(" Dependency on " + dependencyBeingReleased.getArtifactId() + " rewritten to version " + dependencyBeingReleased.getVersionToDependOn());
                } catch (UnresolvedSnapshotDependencyException e) {
                    errors.add(searchingFrom + " references dependency " + e.artifactId + " " + e.version);
                }
            } else
                log.debug(" Dependency on " + dependency.getArtifactId() + " kept at version " + dependency.getVersion());
        }
        for (Plugin plugin : project.getModel().getBuild().getPlugins()) {
            String version = plugin.getVersion();
            if (isSnapshot(resolveVersion(version, projectProperties))) {
                if (!isMultiModuleReleasePlugin(plugin)) {
                    errors.add(searchingFrom + " references plugin " + plugin.getArtifactId() + " " + version);
                }
            }
        }
        return errors;
    }

    private String resolveVersion(String version, Properties projectProperties) {
        if (version != null && version.startsWith("${")) {
            return projectProperties.getProperty(version.replace("${", "").replace("}", ""), version);
        }
        return version;
    }

    private static boolean isMultiModuleReleasePlugin(Plugin plugin) {
        return plugin.getGroupId().equals("com.github.danielflower.mavenplugins") && plugin.getArtifactId().equals("multi-module-maven-release-plugin");
    }

    private boolean isSnapshot(String version) {
        return (version != null && version.endsWith("-SNAPSHOT"));
    }

}
