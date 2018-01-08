package com.github.danielflower.mavenplugins.release.report;/**
 * Created by ondrab on 19.5.17.
 */

import com.github.danielflower.mavenplugins.release.ReleasableModule;
import org.apache.commons.lang3.text.StrLookup;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.maven.plugin.logging.Log;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Ondrej.Bozek@clevermaps.cz
 **/
public class ReportPrinter {
    public static final String CHANGELOG_NAME = "CHANGELOG.md";

    public static final DateFormat df = new SimpleDateFormat("dd.MM.yyyy");
    public static final String TODAY = df.format(new Date());

    public Log log;

    public StrSubstitutor strSubstitutor;
    public String changelogLocation;

    public ReportPrinter(Log log, StrLookup<String> strLookup, String issueIdPrefix, String issueIdSuffix, String changelogLocation) {
        this.log = log;
        this.changelogLocation = changelogLocation;
        strSubstitutor = new StrSubstitutor(strLookup, issueIdPrefix, issueIdSuffix, StrSubstitutor.DEFAULT_ESCAPE);
    }

    public void printModule(ReleasableModule module, String text) {
        try (FileWriter fw = new FileWriter(Paths.get(module.getRelativePathToModule(), CHANGELOG_NAME).toFile())) {
            fw.write(text);
        } catch (IOException e) {
            throw new RuntimeException("Error printing module " + module.getArtifactId(), e);
        }
    }

    public String printChanges(ReleasableModule module) {
        String result = "";
        result += "\n### " + TODAY + " - " + module.getArtifactId() + " " + module.getNewVersion() + "\n\n";
        for (CommitData cd : module.getChangeData().getCommitData()) {
            if (cd.getParents() < 2) {
                String message = cd.getShortMessage();
                message = strSubstitutor.replace(message);
                result += "* " + message + "\n";
            }
        }
        return result;
    }

    public void printChanges(Iterable<ReleasableModule> modules) {
        try (FileWriter fw = new FileWriter(changelogLocation)) {
            fw.write("## RELEASE - " + TODAY + "\n\n");
            for (ReleasableModule mod : modules) {
                String moduleChangeLog = printChanges(mod);
                printModule(mod, moduleChangeLog);
                fw.write(moduleChangeLog);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error printing master changelog.", e);
        }
    }
}
