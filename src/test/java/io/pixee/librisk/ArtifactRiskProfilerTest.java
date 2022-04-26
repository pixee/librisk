package io.pixee.librisk;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ArtifactRiskProfilerTest {

  @Test
  void it_analyzes_struts2_jar() throws IOException {
    File strutsJar = new File("target/struts2-core.jar");
    assertThat(strutsJar.exists(), is(true));
    assertThat(strutsJar.canRead(), is(true));
    assertThat(strutsJar.isFile(), is(true));

    ArtifactRiskProfiler profiler = new DefaultArtifactRiskProfiler();
    ArtifactRiskProfile profile = profiler.profile(strutsJar);

    Set<BinaryBehaviorFound> riskyBehaviors = profile.riskyBehaviors();
    // assertThat(riskyBehaviors, hasItems(Set.of()))
    riskyBehaviors.forEach(System.out::println);
  }
}
