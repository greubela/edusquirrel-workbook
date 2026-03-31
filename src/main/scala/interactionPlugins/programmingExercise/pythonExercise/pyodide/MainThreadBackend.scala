package interactionPlugins.programmingExercise.pythonExercise.pyodide

import com.raquo.laminar.api.L
import interactionPlugins.programmingExercise.pythonExercise.data.*
import interactionPlugins.programmingExercise.pythonExercise.data.PythonUnitTestResult.{GradingStatus, PythonUnitTestGradingResult}
import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideEnvironment.*
import util.web.JsHelpers.promiseToFuture

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

class MainThreadBackend extends PyodideEnvironment {

  private var destroyed = false
  private var initialized = false
  private var pyodideInstance: Option[Pyodide] = None

  private val asyncBackends: mutable.Map[String, AsyncModuleBackend] = mutable.Map.empty
  private val syncBackends: mutable.Map[String, SyncModuleBackend] = mutable.Map.empty

  private def ensureNotDestroyed(): Unit =
    if destroyed then throw new IllegalStateException("This Pyodide environment has been destroyed")

  private def toJsDataVariables(args: Seq[js.Any]): Seq[JsDataVariable] =
    args.zipWithIndex.map { case (value, idx) =>
      JsDataVariable(
        varName = s"arg$idx",
        jsTypeOf = js.typeOf(value),
        stringRepresentation = value.toString
      )
    }

  private def createAsyncModuleProxy(backend: AsyncModuleBackend): js.Object = {
    val handler = js.Dynamic.literal(
      get = { (_: js.Any, prop: js.Any) =>
        val callbackName = prop.toString
        callbackName match {
          case "__name__" => backend.moduleName
          case "__package__" => ""
          case "__doc__" => s"Proxy module for ${backend.moduleName}"
          case "__all__" => backend.exportedNames.toJSArray
          case name if name.startsWith("__") => js.undefined
          case _ =>
            ((rawArgs: js.Array[js.Any]) => {
              backend.handleModuleCall(callbackName, toJsDataVariables(rawArgs.toSeq))
              js.undefined
            }): js.Function1[js.Array[js.Any], js.UndefOr[js.Any]]
        }
      }: js.Function2[js.Any, js.Any, js.Any]
    )

    js.Dynamic
      .newInstance(js.Dynamic.global.Proxy)(js.Dynamic.literal(), handler)
      .asInstanceOf[js.Object]
  }

  private def createSyncModuleProxy(backend: SyncModuleBackend): js.Object = {
    val handler = js.Dynamic.literal(
      get = { (_: js.Any, prop: js.Any) =>
        val callbackName = prop.toString
        callbackName match {
          case "__name__" => backend.moduleName
          case "__package__" => ""
          case "__doc__" => s"Proxy module for ${backend.moduleName}"
          case "__all__" => js.Array(
            "forward", "fd", "backward", "back", "bk", "left", "lt", "right", "rt", "goto", "setpos", "setposition",
            "setx", "sety", "setheading", "seth", "home", "clear", "reset", "clearscreen", "penup", "pu", "up",
            "pendown", "pd", "down", "isdown", "pensize", "width", "pencolor", "fillcolor", "color", "position", "pos",
            "xcor", "ycor", "heading", "distance", "dot", "circle", "bgcolor", "showturtle", "st", "hideturtle", "ht",
            "isvisible", "speed", "tracer", "update", "listen", "onkey", "onclick", "ontimer", "bye", "done", "mainloop",
            "Turtle", "RawTurtle", "Screen", "getscreen"
          )
          case name if name.startsWith("__") => js.undefined
          case _ =>
            ((rawArgs: js.Array[js.Any]) =>
              backend.handleModuleCall(callbackName, toJsDataVariables(rawArgs.toSeq))): js.Function1[js.Array[js.Any], js.Any]
        }
      }: js.Function2[js.Any, js.Any, js.Any]
    )

    js.Dynamic
      .newInstance(js.Dynamic.global.Proxy)(js.Dynamic.literal(), handler)
      .asInstanceOf[js.Object]
  }

  private def initIfNeeded(): Future[Pyodide] = {
    ensureNotDestroyed()
    pyodideInstance match {
      case Some(instance) => Future.successful(instance)
      case None =>
        promiseToFuture(loadPyodide()).map { pyodide =>
          pyodideInstance = Some(pyodide)
          pyodide.runPython(PyodideHelper.helperCode)
          initialized = true
          asyncBackends.values.foreach(backend => pyodide.registerJsModule(backend.moduleName, createAsyncModuleProxy(backend)))
          syncBackends.values.foreach(backend => pyodide.registerJsModule(backend.moduleName, createSyncModuleProxy(backend)))
          pyodide
        }
    }
  }

  override def register(asyncBackend: AsyncModuleBackend): Unit = {
    ensureNotDestroyed()
    asyncBackends.update(asyncBackend.moduleName, asyncBackend)
    pyodideInstance.foreach(_.registerJsModule(asyncBackend.moduleName, createAsyncModuleProxy(asyncBackend)))
  }

  def register(syncBackend: SyncModuleBackend): Unit = {
    ensureNotDestroyed()
    syncBackends.update(syncBackend.moduleName, syncBackend)
    pyodideInstance.foreach(_.registerJsModule(syncBackend.moduleName, createSyncModuleProxy(syncBackend)))
  }

  override def executeCodeFull(request: PythonExecutionRequest): Future[PythonExecutionResult] =
    initIfNeeded().flatMap { pyodide =>
      val resultText = pyodide.runPython(
        s"_execute_code_impl(${js.JSON.stringify(request.pythonCode)}, ${js.JSON.stringify(request.maxLinesToExecute.map(_.asInstanceOf[js.Any]).getOrElse(null))})"
      )
      val parsed = js.JSON.parse(resultText.toString).asInstanceOf[js.Dynamic]
      Future.successful(PythonExecutionResult(request, PyodideHelper.toExecutionState(parsed)))
    }

  override def executeCodeLinewise(request: PythonExecutionRequest, updateAtLeastEveryNLines: Int): L.Var[PythonExecutionResult] = {
    val resultVar = L.Var(PyodideHelper.runningExecutionResult(request))
    executeCodeFull(request).foreach(resultVar.set)
    resultVar
  }

  private def executeSingleUnitTest(
      pythonCode: PythonExecutionRequest,
      test: PythonUnitTest
  ): Future[PythonUnitTestGradingResult] =
    initIfNeeded().map { pyodide =>
      val resultText = pyodide.runPython(
        s"_execute_unit_test_impl(${js.JSON.stringify(pythonCode.pythonCode)}, ${js.JSON.stringify(test.testCode)}, ${js.JSON.stringify(test.testName)}, ${js.JSON.stringify(pythonCode.maxLinesToExecute.map(_.asInstanceOf[js.Any]).getOrElse(null))})"
      )
      val parsed = js.JSON.parse(resultText.toString).asInstanceOf[js.Dynamic]
      val executionResult = PythonExecutionResult(pythonCode, PyodideHelper.toExecutionState(parsed))
      val gradingStatus =
        if executionResult.state.runningState == PythonExecutionResult.PythonExecutionRunningState.FINISHED_SUCCESS then GradingStatus.SUCCESS
        else GradingStatus.FAILED
      PythonUnitTestGradingResult(test = test, result = executionResult, gradingStatus = gradingStatus)
    }

  override def executeUnitTestsFull(
      pythonCode: PythonExecutionRequest,
      unitTests: List[PythonUnitTest]
  ): Future[PythonUnitTestResult] =
    Future.sequence(unitTests.map(executeSingleUnitTest(pythonCode, _))).map { tests =>
      PythonUnitTestResult(userCode = pythonCode, tests = tests.toSet)
    }

  override def executeUnitTestLinewise(
      pythonCode: PythonExecutionRequest,
      unitTests: List[PythonUnitTest],
      updateAtLeastEveryNLines: Int
  ): L.Var[PythonUnitTestResult] = {
    val initial = unitTests.map(test => PythonUnitTestGradingResult(test, PyodideHelper.runningExecutionResult(pythonCode), GradingStatus.UNFINISHED)).toSet
    val resultVar = L.Var(PythonUnitTestResult(pythonCode, initial))

    unitTests.foldLeft(Future.successful(List.empty[PythonUnitTestGradingResult])) { (accFuture, test) =>
      for {
        acc <- accFuture
        next <- executeSingleUnitTest(pythonCode, test)
      } yield {
        val finished = (acc :+ next).toSet
        val unfinished = unitTests.filterNot(testCase => finished.exists(_.test == testCase)).map { remaining =>
          PythonUnitTestGradingResult(remaining, PyodideHelper.runningExecutionResult(pythonCode), GradingStatus.UNFINISHED)
        }
        resultVar.set(PythonUnitTestResult(pythonCode, finished ++ unfinished))
        acc :+ next
      }
    }

    resultVar
  }

  override def resetImportsAndState(): Unit = {
    if initialized then pyodideInstance.foreach(_.runPython(PyodideHelper.helperCode))
  }

  override def destroy(): Unit = {
    destroyed = true
    initialized = false
    pyodideInstance = None
    asyncBackends.clear()
    syncBackends.clear()
  }
}
