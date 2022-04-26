package io.pixee.librisk;

import java.io.File;
import java.util.List;

/**
 * This is the main entrypoint which will allow callers to see the differences between a binary and
 * a source.
 */
public interface SourceAndArtifactProfiler {

  SourceAndArtifactComparison compareCodeAndBinary(
      final List<String> gitRepositoryUrls, final String binaryUrl);

  SourceAndArtifactComparison compareCodeAndBinary(
      final List<File> gitRepositoryRoots, final File binary);
}
