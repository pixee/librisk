package io.pixee.librisk;

import java.util.Set;

record DefaultArtifactRiskProfile(
    Set<BinaryBehaviorFound> riskyBehaviors, Set<String> failedClasses)
    implements ArtifactRiskProfile {}
