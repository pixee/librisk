package io.pixee.librisk;

public record BinaryBehaviorFound(
    Behavior behavior, BinaryLocation location, MethodInvocation methodInvocation) {}
