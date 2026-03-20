package interactionPlugins.programmingExercise.pythonExercise.pyodide

import scala.concurrent.Future

private[pyodide] trait Backend {
  def registerModule(binding: ModuleBinding): Future[Unit]
  def executeCode(pythonCode: String, maxExecutedLines: Option[Int]): Future[ExecutionResult]
  def executeUnitTest(
      pythonCode: String,
      pyUnitTest: PythonUnitTest,
      maxExecutedLines: Option[Int]
  ): Future[PythonUnitTestResult]
  def destroy(): Unit
}
