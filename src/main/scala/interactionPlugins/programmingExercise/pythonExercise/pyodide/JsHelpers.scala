package interactionPlugins.programmingExercise.pythonExercise.pyodide

import com.raquo.laminar.api.L.Var

import interactionPlugins.programmingExercise.pythonExercise.data.*
import interactionPlugins.programmingExercise.pythonExercise.pyodide.*
import interactionPlugins.programmingExercise.pythonExercise.data.PythonExecutionResult.*
import interactionPlugins.programmingExercise.pythonExercise.data.PythonUnitTestResult.*
import interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideEnvironment.*

import scala.concurrent.{Future, Promise}
import scala.scalajs.js

object JsHelpers {
/*
  def promiseToFuture[A](p: js.Promise[A]): Future[A] = {
    val pr = Promise[A]()
    p.`then`[Unit](
      (value: A) => {
        pr.success(value)
        ()
      },
      (err: Any) => {
        pr.failure(js.JavaScriptException(err))
        ()
      }
    )
    pr.future
  }

  def appendVar(v: Var[String], s: String): Unit =
    v.update(_ + s)

  def clearVar(v: Var[String]): Unit =
    v.set("")

  def jsonObjectToMap(value: js.Any): Map[String, String] =
    if value == null || js.isUndefined(value) then Map.empty
    else value.asInstanceOf[js.Dictionary[String]].toMap

  def optString(value: js.Any): Option[String] =
    if value == null || js.isUndefined(value) then None
    else if js.typeOf(value) == "string" then Some(value.asInstanceOf[String])
    else None

  def jsonArrayToList(value: js.Any): List[String] =
    if value == null || js.isUndefined(value) then Nil
    else value.asInstanceOf[js.Array[String]].toList

  def intValue(value: js.Any): Int =
    if value == null || js.isUndefined(value) then 0
    else value.asInstanceOf[Double].toInt

  def boolValue(value: js.Any): Boolean =
    if value == null || js.isUndefined(value) then false
    else value.asInstanceOf[Boolean]

  def optInt(value: js.Any): Option[Int] =
    if value == null || js.isUndefined(value) then None
    else if js.typeOf(value) == "string" then value.asInstanceOf[String].toIntOption
    else Some(value.asInstanceOf[Double].toInt)

  def decodeJsonResult(value: js.Any): js.Dynamic =
    js.JSON.parse(value.asInstanceOf[String]).asInstanceOf[js.Dynamic]

  def parseExecutionResult(code: String, stdout: String, stderr: String, parsed: js.Dynamic): ExecutionResult =
    ExecutionResult(
      code = code,
      success = boolValue(parsed.success),
      stdout = stdout,
      stderr = stderr,
      globals = jsonObjectToMap(parsed.globals),
      locals = jsonObjectToMap(parsed.locals),
      exception = optString(parsed.exception),
      linesExecuted = intValue(parsed.linesExecuted),
      maxExecutedLines = optInt(parsed.maxExecutedLines),
      lineLimitHit = boolValue(parsed.lineLimitHit)
    )
    
 */
}
