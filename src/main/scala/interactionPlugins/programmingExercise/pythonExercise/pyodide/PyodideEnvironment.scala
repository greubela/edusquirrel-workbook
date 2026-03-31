package interactionPlugins.programmingExercise.pythonExercise.pyodide

import com.raquo.laminar.api.L.Var

import interactionPlugins.programmingExercise.pythonExercise.data.*
import interactionPlugins.programmingExercise.pythonExercise.pyodide.*
import interactionPlugins.programmingExercise.pythonExercise.data.PythonExecutionResult.*
import interactionPlugins.programmingExercise.pythonExercise.data.PythonUnitTestResult.*
import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideEnvironment.*

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

final class PyodideEnvironment(
                                backend: ExecutionBackend
                              ) {
  private val delegate: Backend =
    backend match {
      case ExecutionBackend.MainThread => new MainThreadBackend()
      case ExecutionBackend.Worker => new WorkerBackend()
    }

  def registerSyncModule(
                          moduleName: String,
                          callbacks: Map[String, Seq[js.Any] => js.Any]
                        ): Future[Unit] = delegate.registerSyncModule(moduleName, callbacks)

  def registerAsyncModule(
                           moduleName: String,
                           callbacks: Map[String, Seq[js.Any] => Unit]
                         ): Future[Unit] = delegate.registerAsyncModule(moduleName, callbacks)

  def registerModule(
                      moduleName: String,
                      callbacks: Map[String, Seq[js.Any] => js.Any]
                    ): Future[Unit] = registerSyncModule(moduleName, callbacks)
  

  def executeCodeFull(
                       request: PythonExecutionRequest
                     ): 
  Future[PythonExecutionResult] = delegate.executeCodeFull(request)

  def executeCodeLinewise(
                           request: PythonExecutionRequest,
                           updateAtLeastEveryNLines: Int = 1
                         ):
  Var[PythonExecutionResult] = delegate.executeCodeLinewise(request, updateAtLeastEveryNLines)

  def executeUnitTestsFull(
                            pythonCode: PythonExecutionRequest,
                            unitTests: List[PythonUnitTest],
                          ):
  Future[PythonUnitTestResult] = delegate.executeUnitTestsFull(pythonCode, unitTests)

  def executeUnitTestLinewise(
                               pythonCode: PythonExecutionRequest,
                               unitTests: List[PythonUnitTest],
                               updateAtLeastEveryNLines: Int = 100,
                             ):
  Var[PythonUnitTestResult] = delegate.executeUnitTestLinewise(pythonCode, unitTests, updateAtLeastEveryNLines)
    
  def destroy(): Unit =
    delegate.destroy()
}

object PyodideEnvironment {

  trait Backend {

    def registerSyncModule(
                            moduleName: String,
                            callbacks: Map[String, Seq[js.Any] => js.Any]
                          ): Future[Unit]

    def registerAsyncModule(
                             moduleName: String,
                             callbacks: Map[String, Seq[js.Any] => Unit]
                           ): Future[Unit]

    def executeCodeFull(
                         request: PythonExecutionRequest
                       ): Future[PythonExecutionResult] 

    def executeCodeLinewise(
                             request: PythonExecutionRequest,
                             updateAtLeastEveryNLines: Int = 1
                           ): Var[PythonExecutionResult]

    def executeUnitTestsFull(
                              pythonCode: PythonExecutionRequest,
                              unitTests: List[PythonUnitTest],
                            ): Future[PythonUnitTestResult]

    def executeUnitTestLinewise(
                                 pythonCode: PythonExecutionRequest,
                                 unitTests: List[PythonUnitTest],
                                 updateAtLeastEveryNLines: Int = 100,
                               ): Var[PythonUnitTestResult]

    def destroy(): Unit
  }


  @js.native
  trait Pyodide extends js.Object {
    def registerJsModule(name: String, module: js.Object): Unit = js.native

    def setStdout(options: js.Object): Unit = js.native

    def setStderr(options: js.Object): Unit = js.native

    def runPython(code: String): js.Any = js.native

    def runPythonAsync(code: String, options: js.Object): js.Promise[js.Any] = js.native
  }

  @js.native
  @JSGlobal("loadPyodide")
  def loadPyodide(): js.Promise[Pyodide] = js.native

  sealed trait ExecutionBackend derives CanEqual

  object ExecutionBackend {
    case object MainThread extends ExecutionBackend

    case object Worker extends ExecutionBackend
  }


}
