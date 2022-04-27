package io.pixee.librisk;

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
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
  void it_analyzes_system_commands_jar() throws IOException {
    String jarEntryPath = DoesSystemCommands.class.getName().replace('.', '/') + ".class";
    String classFilePath = "target/test-classes/" + jarEntryPath;
    File classFile = new File(classFilePath);
    ClassEntry entry = toClassEntry(classFile, jarEntryPath);
    when(jarReader.nextClassNode()).thenReturn(Optional.of(entry), Optional.empty());

    ArtifactRiskProfiler profiler = new DefaultArtifactRiskProfiler(jarLoader);
    ArtifactRiskProfile profile = profiler.profile(classFile);

    Set<BinaryBehaviorFound> riskyBehaviors = profile.riskyBehaviors();

    MethodDescriptor doesRuntimeExecMethod =
        new MethodDescriptor("doesRuntimeExec", "void", List.of(), Optional.of(8));
    MethodInvocation runtimeExecMethod =
        new MethodInvocation(
            "java/lang/Runtime", "exec", "(Ljava/lang/String;)Ljava/lang/Process;");
    Optional<Integer> runtimeExecLine = Optional.of(8);

    MethodDescriptor doesProcessBuilderInitMethod =
        new MethodDescriptor("doesProcessBuilderInit", "void", List.of(), Optional.of(12));
    MethodInvocation processBuilderInitMethod =
        new MethodInvocation("java/lang/ProcessBuilder", "<init>", "([Ljava/lang/String;)V");
    Optional<Integer> processBuilderInitLine = Optional.of(12);

    MethodDescriptor doesProcessBuilderStartMethod =
        new MethodDescriptor(
            "doesProcessBuilderStart",
            "void",
            List.of("java.lang.ProcessBuilder"),
            Optional.of(16));
    MethodInvocation processBuilderStartMethod =
        new MethodInvocation("java/lang/ProcessBuilder", "start", "()Ljava/lang/Process;");
    Optional<Integer> processBuilderStartLine = Optional.of(16);

    MethodDescriptor doesProcessBuilderCommandMethod =
        new MethodDescriptor(
            "doesProcessBuilderCommand",
            "void",
            List.of("int", "java.lang.ProcessBuilder"),
            Optional.of(20));
    MethodInvocation processBuilderCommandMethod =
        new MethodInvocation("java/lang/ProcessBuilder", "command", "()Ljava/util/List;");
    Optional<Integer> processBuilderCommandLine = Optional.of(20);

    assertThat(
        riskyBehaviors.contains(
            new BinaryBehaviorFound(
                Behavior.SYSTEM_COMMANDS,
                new BinaryLocation(jarEntryPath, doesRuntimeExecMethod, runtimeExecLine),
                runtimeExecMethod)),
        is(true));

    BinaryBehaviorFound processBuilderInitBehavior =
        new BinaryBehaviorFound(
            Behavior.SYSTEM_COMMANDS,
            new BinaryLocation(jarEntryPath, doesProcessBuilderInitMethod, processBuilderInitLine),
            processBuilderInitMethod);
    assertThat(riskyBehaviors.contains(processBuilderInitBehavior), is(true));
    assertThat(
        riskyBehaviors.contains(
            new BinaryBehaviorFound(
                Behavior.SYSTEM_COMMANDS,
                new BinaryLocation(
                    jarEntryPath, doesProcessBuilderStartMethod, processBuilderStartLine),
                processBuilderStartMethod)),
        is(true));

    BinaryBehaviorFound processBuilderCommandBehavior =
        new BinaryBehaviorFound(
            Behavior.SYSTEM_COMMANDS,
            new BinaryLocation(
                jarEntryPath, doesProcessBuilderCommandMethod, processBuilderCommandLine),
            processBuilderCommandMethod);
    assertThat(riskyBehaviors.contains(processBuilderCommandBehavior), is(true));
  }

  ClassEntry toClassEntry(final File classFile, final String jarEntryPath) throws IOException {
    byte[] bytes = FileUtils.readFileToByteArray(classFile);
    ClassReader reader = new ClassReader(bytes);
    ClassNode node = new ClassNode();
    reader.accept(node, 0);
    return new ClassEntry(node, jarEntryPath);
  }
}
