package interactionPlugins.programmingExercise.pythonExercise.pyodide

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.Var
import interactionPlugins.programmingExercise.pythonExercise.data.*
import interactionPlugins.programmingExercise.pythonExercise.data.PythonExecutionResult.PythonExecutionRunningState
import interactionPlugins.programmingExercise.pythonExercise.data.PythonUnitTestResult.{GradingStatus, PythonUnitTestGradingResult}
import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideEnvironment.AsyncModuleBackend
import org.scalajs.dom
import util.web.JsHelpers.*

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

class PyodideWorkerEnvironment extends PyodideEnvironment {

  private val worker =
    new dom.Worker(
      "./js/PyodideLoader.js",
      js.Dynamic.literal(`type` = "module").asInstanceOf[dom.WorkerOptions]
    )


  // todo 

  protected val registeredBackends: mutable.ListBuffer[AsyncModuleBackend] = ???
  /*
    worker.onmessage = { (e: dom.MessageEvent) =>
      val data = e.data.asInstanceOf[js.Dynamic]
      data.`type`.asInstanceOf[String] match {
        case "module-callback" =>
          val moduleName = data.moduleName.asInstanceOf[String]
          val functionName = data.functionName.asInstanceOf[String]
          val args = data.args.asInstanceOf[js.Array[js.Any]].toSeq
        //moduleCallbacks.get((moduleName, functionName)).foreach(cb => cb(args))
        case "ready" | "registered" | "result" | "error" =>
          val requestId = data.requestId.asInstanceOf[String]
          pending.remove(requestId).foreach { p =>
            if data.`type`.asInstanceOf[String] == "error" then p.failure(new RuntimeException(data.error.asInstanceOf[String]))
            else p.success(data)
          }
        case _ => ()
      }
    }: js.Function1[dom.MessageEvent, Any]
  */

  /*

  override def executeCodeFull(request: PythonExecutionRequest): Future[PythonExecutionResult] =
    init().flatMap { _ =>
      val payload = js.Dictionary[js.Any]("code" -> request.pythonCode)
      request.maxLinesToExecute.foreach(limit => payload.update("maxExecutedLines", limit))
      send("executeCode", payload).map { msg =>
        val parsed = msg.payload.asInstanceOf[js.Dynamic]
        PythonExecutionResult(request, toExecutionState(parsed))
      }
    }

  override def executeCodeLinewise(
      request: PythonExecutionRequest,
      updateAtLeastEveryNLines: Int
  ): Var[PythonExecutionResult] = {
    val resultVar = Var(runningExecutionResult(request))
    executeCodeFull(request).foreach(resultVar.set)
    resultVar
  }

  private def executeSingleUnitTest(
      pythonCode: PythonExecutionRequest,
      test: PythonUnitTest
  ): Future[PythonUnitTestGradingResult] =
    init().flatMap { _ =>
      val payload = js.Dictionary[js.Any](
        "code" -> pythonCode.pythonCode,
        "testCode" -> test.testCode,
        "testName" -> test.testName
      )
      pythonCode.maxLinesToExecute.foreach(limit => payload.update("maxExecutedLines", limit))
      send("executeUnitTest", payload).map { msg =>
        val parsed = msg.payload.asInstanceOf[js.Dynamic]
        val executionResult = PythonExecutionResult(pythonCode, toExecutionState(parsed))
        val gradingStatus = if asBoolean(parsed.success) then GradingStatus.SUCCESS else GradingStatus.FAILED
        PythonUnitTestGradingResult(test = test, result = executionResult, gradingStatus = gradingStatus)
      }
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
  ): Var[PythonUnitTestResult] = {
    val initial = unitTests.map { test =>
      PythonUnitTestGradingResult(test, runningExecutionResult(pythonCode), GradingStatus.UNFINISHED)
    }
    val resultVar = Var(PythonUnitTestResult(pythonCode, initial.toSet))

    unitTests.foldLeft(Future.successful(List.empty[PythonUnitTestGradingResult])) { (accFuture, test) =>
      for {
        acc <- accFuture
        next <- executeSingleUnitTest(pythonCode, test)
      } yield {
        val updated = (acc :+ next).toSet
        val unfinished = unitTests.filterNot(t => updated.exists(_.test == t)).map { remaining =>
          PythonUnitTestGradingResult(remaining, runningExecutionResult(pythonCode), GradingStatus.UNFINISHED)
        }
        resultVar.set(PythonUnitTestResult(pythonCode, updated ++ unfinished))
        acc :+ next
      }
    }

    resultVar
  }

  override def destroy(): Unit = {
    worker.postMessage(js.Dynamic.literal(`type` = "destroy"))
    worker.terminate()
    pending.values.foreach(_.tryFailure(new RuntimeException("Worker destroyed")))
    pending.clear()
    initialized = false
  }
  
  
   */

  override def register(asyncBackend: AsyncModuleBackend): Unit = ???

  override def executeCodeFull(request: PythonExecutionRequest): Future[PythonExecutionResult] = ???

  override def executeCodeLinewise(request: PythonExecutionRequest, updateAtLeastEveryNLines: Int): L.Var[PythonExecutionResult] = ???

  override def executeUnitTestsFull(pythonCode: PythonExecutionRequest, unitTests: List[PythonUnitTest]): Future[PythonUnitTestResult] = ???

  override def executeUnitTestLinewise(pythonCode: PythonExecutionRequest, unitTests: List[PythonUnitTest], updateAtLeastEveryNLines: Int): L.Var[PythonUnitTestResult] = ???

  override def resetImportsAndState(): Unit = ???

  override def destroy(): Unit = ???
}
