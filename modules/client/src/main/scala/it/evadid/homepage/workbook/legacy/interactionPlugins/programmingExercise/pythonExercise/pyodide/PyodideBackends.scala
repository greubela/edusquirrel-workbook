package it.evadid.homepage.workbook.legacy.interactionPlugins.programmingExercise.pythonExercise.pyodide

import scala.scalajs.js
import PyodideBackends.*
import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.svg.{SvgPathBuilderCommand, TurtlePathBuilder}

object PyodideBackends {

  case class CallbackLibrary(moduleName: String, methodMap: Map[String, Vector[js.Any] => Unit])
  
  final case class JsDataVariable(
      varName: String,
      jsTypeOf: String,
      stringRepresentation: String
  )

  trait SyncModuleBackend {
    def moduleName: String

    def handleModuleCall(callbackName: String, args: Seq[JsDataVariable]): js.Any
  }

  final case class CallbackOp(
                               module: String,
                               method: String,
                               args: Vector[js.Any]
                             )

  final case class PythonRunConfig(
                                    context: js.Dictionary[js.Any] = js.Dictionary.empty,
                                    resetGlobals: Boolean = true,
                                    captureStdout: Boolean = true,
                                    captureStderr: Boolean = true
                                  )

  final case class PythonRunReport(
                                    callbackOps: Vector[CallbackOp],
                                    stdout: String,
                                    stderr: String
                                  )

  final case class TurtleExecutionResult[T: Fractional](
                                                         regularExecutionResult: PythonRunReport,
                                                         startPoint: Point[T],
                                                         turtleState: TurtlePathBuilder.TurtleState[T],
                                                         turtleCommands: List[TurtlePathBuilder.TurtleCommand[T]],
                                                         svgPathBuilderCommands: List[SvgPathBuilderCommand[T]]
                                                        )

  final case class PythonWorkerFailure(
                                        message: String,
                                        stdout: String,
                                        stderr: String
                                      ) extends RuntimeException(message)
  
}
