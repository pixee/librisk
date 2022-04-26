package io.pixee.librisk;

import java.io.File;
import java.io.IOException;

/**
 * This is the main entrypoint which will allow callers to get the risk profile of a given library.
 */
public interface ArtifactRiskProfiler {

  ArtifactRiskProfile profile(final File binary) throws IOException;

  static ArtifactRiskProfiler createDefault() {
    return new DefaultArtifactRiskProfiler();
  }
}
