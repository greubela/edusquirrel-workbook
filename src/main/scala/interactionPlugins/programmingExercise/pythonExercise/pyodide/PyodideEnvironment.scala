package interactionPlugins.programmingExercise.pythonExercise.pyodide

import com.raquo.laminar.api.L.Var

import scala.concurrent.Future

final class PyodideEnvironment(
    backend: ExecutionBackend,
    stdoutVar: Var[String],
    stderrVar: Var[String]
) {

  private val delegate: Backend =
    backend match {
      case ExecutionBackend.MainThread => new MainThreadBackend(stdoutVar, stderrVar)
      case ExecutionBackend.Worker     => new WorkerBackend(stdoutVar, stderrVar)
    }

  def registerModuleInEnvironment(binding: ModuleBinding): Future[Unit] =
    delegate.registerModule(binding)

  def executeCode(pythonCode: String): Future[ExecutionResult] =
    delegate.executeCode(pythonCode, None)

  def executeCodeWithLineLimit(pythonCode: String, maxExecutedLines: Int): Future[ExecutionResult] =
    delegate.executeCode(pythonCode, Some(maxExecutedLines))

  def executeUnitTest(pythonCode: String, pyUnitTest: PythonUnitTest): Future[PythonUnitTestResult] =
    delegate.executeUnitTest(pythonCode, pyUnitTest, None)

  def executeUnitTestWithLineLimit(
      pythonCode: String,
      pyUnitTest: PythonUnitTest,
      maxExecutedLines: Int
  ): Future[PythonUnitTestResult] =
    delegate.executeUnitTest(pythonCode, pyUnitTest, Some(maxExecutedLines))

  def destroy(): Unit =
    delegate.destroy()
}
