package interactionPlugins.programmingExercise.pythonExercise.pyodide

import com.raquo.laminar.api.L
import interactionPlugins.programmingExercise.pythonExercise.data.*
import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideEnvironment.*

import scala.concurrent.Future

class MainThreadBackend extends PyodideEnvironment {

  def register(syncBackend: SyncModuleBackend): Unit = ???

  // todo

  override def register(asyncBackend: AsyncModuleBackend): Unit = ???

  override def executeCodeFull(request: PythonExecutionRequest): Future[PythonExecutionResult] = ???

  override def executeCodeLinewise(request: PythonExecutionRequest, updateAtLeastEveryNLines: Int): L.Var[PythonExecutionResult] = ???

  override def executeUnitTestsFull(pythonCode: PythonExecutionRequest, unitTests: List[PythonUnitTest]): Future[PythonUnitTestResult] = ???

  override def executeUnitTestLinewise(pythonCode: PythonExecutionRequest, unitTests: List[PythonUnitTest], updateAtLeastEveryNLines: Int): L.Var[PythonUnitTestResult] = ???

  override def resetImportsAndState(): Unit = ???

  override def destroy(): Unit = ???
}
