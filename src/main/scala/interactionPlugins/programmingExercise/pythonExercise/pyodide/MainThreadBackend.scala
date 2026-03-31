package interactionPlugins.programmingExercise.pythonExercise.pyodide

import com.raquo.laminar.api.L
import interactionPlugins.programmingExercise.pythonExercise.data.*
import interactionPlugins.programmingExercise.pythonExercise.data.PythonUnitTestResult.{GradingStatus, PythonUnitTestGradingResult}
import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideEnvironment.*
import util.web.JsHelpers.{asBoolean, asStringOption, asStringSeq, promiseToFuture}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

class MainThreadBackend extends PyodideEnvironment {

  private var destroyed = false
  private var initialized = false
  private var pyodideInstance: Option[Pyodide] = None
  private var initFuture: Option[Future[Pyodide]] = None

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

  private def rawArgsToSeq(rawArgs: js.Any): Seq[js.Any] =
    if js.isUndefined(rawArgs) || rawArgs == null then Seq.empty
    else if js.Array.isArray(rawArgs) then rawArgs.asInstanceOf[js.Array[js.Any]].toSeq
    else Seq(rawArgs)

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
            ((rawArgs: js.Any) => {
              backend.handleModuleCall(callbackName, toJsDataVariables(rawArgsToSeq(rawArgs)))
              js.undefined
            }): js.Function1[js.Any, js.UndefOr[js.Any]]
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
            ((rawArgs: js.Any) =>
              backend.handleModuleCall(callbackName, toJsDataVariables(rawArgsToSeq(rawArgs)))): js.Function1[js.Any, js.Any]
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
        initFuture.getOrElse {
          val future = promiseToFuture(loadPyodide()).map { pyodide =>
            pyodideInstance = Some(pyodide)
            pyodide.runPython(PyodideHelper.helperCode)
            initialized = true
            asyncBackends.values.foreach(backend => pyodide.registerJsModule(backend.moduleName, createAsyncModuleProxy(backend)))
            syncBackends.values.foreach(backend => pyodide.registerJsModule(backend.moduleName, createSyncModuleProxy(backend)))
            pyodide
          }

          initFuture = Some(future)
          future.andThen {
            case scala.util.Success(_) => initFuture = None
            case scala.util.Failure(_) => initFuture = None
          }
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

  private def executeUnitTestsBatch(
      pythonCode: PythonExecutionRequest,
      unitTests: List[PythonUnitTest]
  ): Future[List[PythonUnitTestGradingResult]] =
    initIfNeeded().map { pyodide =>
      val testsPayload = unitTests.zipWithIndex.map { case (test, idx) =>
        js.Dynamic.literal(index = idx, testName = test.testName, testCode = test.testCode)
      }.toJSArray
      val resultText = pyodide.runPython(
        s"_execute_unit_tests_batch_impl(${js.JSON.stringify(pythonCode.pythonCode)}, ${js.JSON.stringify(testsPayload)}, ${js.JSON.stringify(pythonCode.maxLinesToExecute.map(_.asInstanceOf[js.Any]).getOrElse(null))})"
      )
      val parsedBatch = js.JSON.parse(resultText.toString).asInstanceOf[js.Dynamic]
      val lineLimitHit = asBoolean(parsedBatch.lineLimitHit)
      val sharedStdout = asStringOption(parsedBatch.stdout).getOrElse("")
      val sharedStderr = asStringOption(parsedBatch.stderr).getOrElse("")
      val batchException = asStringOption(parsedBatch.exception).orNull

      parsedBatch.tests.asInstanceOf[js.Array[js.Dynamic]].toSeq.map { testPayload =>
        val index = testPayload.index.asInstanceOf[Int]
        val test = unitTests(index)
        val details = (asStringSeq(testPayload.failures) ++ asStringSeq(testPayload.errors)).mkString("\n").trim
        val combinedStderr = List(sharedStderr, asStringOption(testPayload.stderr).getOrElse(""), details).filter(_.nonEmpty).mkString("\n")

        val parsedExecutionPayload = js.Dynamic.literal(
          success = asBoolean(testPayload.success) && !lineLimitHit,
          stdout = sharedStdout + asStringOption(testPayload.stdout).getOrElse(""),
          stderr = combinedStderr,
          exception = if lineLimitHit then batchException else asStringOption(testPayload.exception).orNull,
          globals = parsedBatch.globals,
          locals = parsedBatch.locals,
          linesExecuted = parsedBatch.linesExecuted,
          lineLimitHit = parsedBatch.lineLimitHit
        )
        val executionResult = PythonExecutionResult(pythonCode, PyodideHelper.toExecutionState(parsedExecutionPayload))
        val gradingStatus =
          if executionResult.state.runningState == PythonExecutionResult.PythonExecutionRunningState.FINISHED_SUCCESS then GradingStatus.SUCCESS
          else GradingStatus.FAILED

        PythonUnitTestGradingResult(test = test, result = executionResult, gradingStatus = gradingStatus)
      }.toList
    }

  override def executeUnitTestsFull(
      pythonCode: PythonExecutionRequest,
      unitTests: List[PythonUnitTest]
  ): Future[PythonUnitTestResult] =
    executeUnitTestsBatch(pythonCode, unitTests).map { tests =>
      PythonUnitTestResult(userCode = pythonCode, tests = tests.toSet)
    }

  override def executeUnitTestLinewise(
      pythonCode: PythonExecutionRequest,
      unitTests: List[PythonUnitTest],
      updateAtLeastEveryNLines: Int
  ): L.Var[PythonUnitTestResult] = {
    val initial = unitTests.map(test => PythonUnitTestGradingResult(test, PyodideHelper.runningExecutionResult(pythonCode), GradingStatus.UNFINISHED)).toSet
    val resultVar = L.Var(PythonUnitTestResult(pythonCode, initial))

    executeUnitTestsBatch(pythonCode, unitTests).foreach { completed =>
      resultVar.set(PythonUnitTestResult(pythonCode, completed.toSet))
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
    initFuture = None
    asyncBackends.clear()
    syncBackends.clear()
  }
}
