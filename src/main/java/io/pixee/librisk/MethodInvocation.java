package io.pixee.librisk;

/** Describes a method invocation instruction (not a method within a class.) */
public record MethodInvocation(String owner, String name, String desc) {}
