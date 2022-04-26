package io.pixee.librisk;

import java.util.Set;

public interface ArtifactRiskProfile {

  //    /**
  //     * Describe the attack surface
  //     */
  //    InvokableAttackSurface invokableAttackSurface();
  //
  //    /**
  //     * Describe if it looks like it processes external data (e.g., user input)
  //     */
  //    ExternalInteraction externalInteraction();

  /** Describe the application's higher level behaviors that introduce risk. */
  Set<BinaryBehaviorFound> riskyBehaviors();

  Set<String> failedClasses();
}
