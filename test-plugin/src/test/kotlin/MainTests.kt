import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.fail

class MainTests {

  private fun runTest(block: (File) -> Unit = {}): BuildResult? {
    val projectDir = File("build/testProject")
    projectDir.deleteRecursively()
    File("fixtures/test-project").copyRecursively(projectDir)
    block(projectDir)

    // We need at least Gradle 6 for metadata
    return GradleRunner.create().withGradleVersion("6.0")
      .withProjectDir(projectDir)
      .forwardStdOutput(System.out.writer())
      .forwardStdError(System.err.writer())
      .withDebug(true)
      .withArguments("dependencies")
      .build()
  }

  @Test
  fun regularJarFails() {
    try {
      runTest()
      fail("A failure was expected")
    } catch (e: UnexpectedBuildFailure) {
      assert(e.message!!.contains("boolean kotlin.text.StringsKt.contentEquals(java.lang.CharSequence, java.lang.CharSequence)"))
    }
  }

  @Test
  fun gr8JarSucceeds() {
    val result = runTest {
      File(it, "build.gradle.kts").apply {
        // Uncomment the attribute selection
        writeText(readText().replace("//", ""))
      }
    }

    assertEquals(TaskOutcome.SUCCESS, result!!.task(":dependencies")!!.outcome)
  }
}