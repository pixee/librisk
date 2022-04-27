package io.pixee.librisk;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.pixee.librisk.DefaultArtifactRiskProfiler.ClassEntry;
import io.pixee.librisk.DefaultArtifactRiskProfiler.JarLoader;
import io.pixee.librisk.DefaultArtifactRiskProfiler.JarReader;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

final class ArtifactRiskProfilerTest {

  private JarLoader jarLoader;
  private JarReader jarReader;

  @BeforeEach
  void setup() throws IOException {
    jarLoader = mock(JarLoader.class);
    jarReader = mock(JarReader.class);
    when(jarLoader.load(any(File.class))).thenReturn(jarReader);
  }

  @Test
  void it_scans_jar() throws IOException {
    File struts2Jar = new File("target/spring-web.jar");
    assertThat(struts2Jar.exists(), is(true));

    ArtifactRiskProfiler profiler = ArtifactRiskProfiler.createDefault();
    ArtifactRiskProfile profile = profiler.profile(struts2Jar);
    assertThat(profile.riskyBehaviors(), hasItems());
    assertThat(profile.failedClasses(), hasItems());
  }

  private static Stream<Arguments> systemCommandsArguments() {
    return Stream.of(
        Arguments.of(
            DoesSystemCommands.class,
            List.of(
                behavior(
                    Behavior.SYSTEM_COMMANDS,
                    withinMethod(
                        DoesSystemCommands.class, "doesRuntimeExec", "void", List.of(), 8, 8),
                    withInvocation(
                        "java/lang/Runtime", "exec", "(Ljava/lang/String;)Ljava/lang/Process;")),
                behavior(
                    Behavior.SYSTEM_COMMANDS,
                    withinMethod(
                        DoesSystemCommands.class,
                        "doesProcessBuilderInit",
                        "void",
                        List.of(),
                        12,
                        12),
                    withInvocation("java/lang/ProcessBuilder", "<init>", "([Ljava/lang/String;)V")),
                behavior(
                    Behavior.SYSTEM_COMMANDS,
                    withinMethod(
                        DoesSystemCommands.class,
                        "doesProcessBuilderStart",
                        "void",
                        List.of("java.lang.ProcessBuilder"),
                        16,
                        16),
                    withInvocation("java/lang/ProcessBuilder", "start", "()Ljava/lang/Process;")),
                behavior(
                    Behavior.SYSTEM_COMMANDS,
                    withinMethod(
                        DoesSystemCommands.class,
                        "doesProcessBuilderCommand",
                        "void",
                        List.of("int", "java.lang.ProcessBuilder"),
                        20,
                        20),
                    withInvocation("java/lang/ProcessBuilder", "command", "()Ljava/util/List;")))),
        Arguments.of(
            DoesDeserialization.class,
            List.of(
                behavior(
                    Behavior.DESERIALIZATION,
                    withinMethod(
                        DoesDeserialization.class,
                        "doesReadObject",
                        "void",
                        List.of("java.io.ObjectInputStream"),
                        9,
                        9),
                    withInvocation(
                        "java/io/ObjectInputStream", "readObject", "()Ljava/lang/Object;")),
                behavior(
                    Behavior.DESERIALIZATION,
                    withinMethod(
                        DoesDeserialization.class,
                        "doesDefaultReadObject",
                        "void",
                        List.of("java.io.ObjectInputStream"),
                        14,
                        15),
                    withInvocation("java/io/ObjectInputStream", "defaultReadObject", "()V")),
                behavior(
                    Behavior.DESERIALIZATION,
                    withinMethod(
                        DoesDeserialization.class,
                        "doesKryo",
                        "void",
                        List.of("com.esotericsoftware.kryo.Kryo"),
                        19,
                        19),
                    withInvocation(
                        "com/esotericsoftware/kryo/Kryo",
                        "readObject",
                        "(Lcom/esotericsoftware/kryo/io/Input;Ljava/lang/Class;)Ljava/lang/Object;")))));
  }

  @ParameterizedTest
  @MethodSource("systemCommandsArguments")
  void it_analyzes_system_commands(
      final Class<?> testClass, final List<BinaryBehaviorFound> expectedBehaviors)
      throws IOException {
    String jarEntryPath = testClass.getName().replace('.', '/') + ".class";
    String classFilePath = "target/test-classes/" + jarEntryPath;
    File classFile = new File(classFilePath);
    ClassEntry entry = toClassEntry(classFile, jarEntryPath);
    when(jarReader.nextClassNode()).thenReturn(Optional.of(entry), Optional.empty());

    ArtifactRiskProfiler profiler = new DefaultArtifactRiskProfiler(jarLoader);
    ArtifactRiskProfile profile = profiler.profile(classFile);

    Set<BinaryBehaviorFound> riskyBehaviors = profile.riskyBehaviors();

    assertThat(riskyBehaviors, hasItems(expectedBehaviors.toArray(new BinaryBehaviorFound[0])));
  }

  ClassEntry toClassEntry(final File classFile, final String jarEntryPath) throws IOException {
    byte[] bytes = FileUtils.readFileToByteArray(classFile);
    ClassReader reader = new ClassReader(bytes);
    ClassNode node = new ClassNode();
    reader.accept(node, 0);
    return new ClassEntry(node, jarEntryPath);
  }

  /**
   * These are a collection of silly little builders that just reduce keystrokes to create all the
   * domain objects we need in the tests.
   */
  private static BinaryBehaviorFound behavior(
      Behavior behavior, BinaryLocation location, MethodInvocation methodInvocation) {
    return new BinaryBehaviorFound(behavior, location, methodInvocation);
  }

  private static BinaryLocation withinMethod(
      final Class type,
      final String methodName,
      final String returnType,
      final List<String> argTypes,
      final int methodStartLineNumber,
      final int instructionLineNumber) {
    return new BinaryLocation(
        type.getName().replace('.', '/') + ".class",
        new MethodDescriptor(methodName, returnType, argTypes, Optional.of(methodStartLineNumber)),
        Optional.of(instructionLineNumber));
  }

  private static MethodInvocation withInvocation(
      final String owner, final String name, final String desc) {
    return new MethodInvocation(owner, name, desc);
  }
}
