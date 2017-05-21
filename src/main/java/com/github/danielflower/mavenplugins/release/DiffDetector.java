package com.github.danielflower.mavenplugins.release;

import com.github.danielflower.mavenplugins.release.report.ChangeData;

import java.io.IOException;
import java.util.Collection;

public interface DiffDetector {
    ChangeData hasChangedSince(String modulePath, java.util.List<String> childModules, Collection<AnnotatedTag> tags) throws IOException;
}
