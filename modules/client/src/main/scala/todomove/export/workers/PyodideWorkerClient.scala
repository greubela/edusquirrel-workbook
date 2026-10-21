package todomove.`export`.workers

import it.evadid.core.datastructures.vectorShapes.svg.TurtlePathBuilder.{TurtleCommand, TurtleState}
import it.evadid.homepage.workbook.legacy.interactionPlugins.programmingExercise.pythonExercise.pyodide.PyodideBackends.*
import it.evadid.core.datastructures.geometry.{Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.svg.{SvgPathBuilderCommand, TurtlePathBuilder}
import it.evadid.util.JsHelpers.*
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilderCommand.*
import it.evadid.util.JsHelpers
import todomove.`export`.modules.TurtleSingleton

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.*

/** Python jobs and callback replay over the shared Scala.js command worker. */
final class PyodideWorkerClient {
  private val worker = it.evadid.distribution.clients.WebWorkerExecutionClient.fromWorker(
    it.evadid.facades.Workers.classic(it.evadid.homepage.control.startup.BrowserLoading.worker).createLoadedInstance())
  private val callbacks = com.raquo.airstream.state.Var(Map.empty[String, List[String]])

  def addCallbacks(moduleName: String, methodNames: Seq[String]): Future[Unit] = {
    callbacks.update(_ + (moduleName -> methodNames.toList))
    Future.unit
  }

  def run(code: String, config: PythonRunConfig = PythonRunConfig()): Future[PythonRunReport] = {
    import it.evadid.distribution.command.ExecutionCommand
    import it.evadid.distribution.commandTypes.WorkerCommands
    val context = config.context.toMap.view.mapValues(value => ujson.read(js.JSON.stringify(value))).toMap
    val request = WorkerCommands.RunPythonRequest(code, context, callbacks.now(),
      resetGlobals = config.resetGlobals, captureStdout = config.captureStdout, captureStderr = config.captureStderr)
    val logger = it.evadid.util.logging.Logger.withNameAndPrefixes(Some("Python"),
      it.evadid.util.logging.derived.PrintToStdLogger.printNothing)
    worker.executeResponse(logger, ExecutionCommand(WorkerCommands.runPython.name, upickle.default.writeJs(request))).flatMap { reply =>
      reply.result match {
        case Left(error) => Future.failed(PythonWorkerFailure(error.getMessage,
          reply.additionalOutput.loggerOut, reply.additionalOutput.loggerError))
        case Right(encoded) =>
          val result = encoded.getAs[WorkerCommands.RunPythonResult]
          Future.successful(PythonRunReport(result.callbackOps.map(op => CallbackOp(op.module, op.method,
            op.args.map(value => js.JSON.parse(ujson.write(value))).toVector)).toVector, result.stdout, result.stderr))
      }
    }
  }

  def runWithCallbackLibrary(code: String, library: CallbackLibrary, config: PythonRunConfig = PythonRunConfig()): Future[PythonRunReport] = {
    addCallbacks(library.moduleName, library.methodMap.keys.toSeq).flatMap(_ => run(code, config)).map { report =>
      report.callbackOps.filter(_.module == library.moduleName).foreach(op => library.methodMap.get(op.method).foreach(_(op.args)))
      report
    }
  }

  def terminate(): Unit = worker.close()
}

object PyodideWorkerClient {

  private object TurtleFieldKeys {
    val StartPoint = "startPoint"
    val TurtleState = "turtleState"
    val TurtleCommands = "turtleCommands"
    val SvgPathBuilderCommands = "svgPathBuilderCommands"

    val Name = "name"
    val Args = "args"
    val Kind = "kind"

    val X = "x"
    val Y = "y"
    val Dx = "dx"
    val Dy = "dy"
    val Rx = "rx"
    val Ry = "ry"
    val RotationDeg = "rotationDeg"
    val Radius = "radius"

    val HeadingDeg = "headingDeg"
    val PenDown = "penDown"
    val Visible = "visible"
    val LargeArc = "largeArc"
    val Sweep = "sweep"
  }

  def executeTurtleCode[T: Fractional](
                                        worker: PyodideWorkerClient,
                                        turtlePythonCode: String
                                      ): Future[TurtleExecutionResult[T]] = {
    val callbackLibrary = turtleCallbackLibrary()
    val runCode = s"import turtle\n$turtlePythonCode"

    worker
      .addCallbacks(callbackLibrary.moduleName, callbackLibrary.methodMap.keys.toSeq)
      .flatMap(_ => worker.run(runCode))
      .map { runReport =>
      val turtleStateAfterRun = turtleBuilderFromCallbacks[T](runReport.callbackOps)
      TurtleExecutionResult[T](
        regularExecutionResult = runReport,
        startPoint = turtleStateAfterRun.startPoint,
        turtleState = turtleStateAfterRun.turtleState,
        turtleCommands = turtleStateAfterRun.turtleCommands,
        svgPathBuilderCommands = turtleStateAfterRun.pathBuilderCommands
      )
    }
  }

   def turtleBuilderFromCallbacks[T: Fractional](
                                                                   callbackOps: Seq[CallbackOp]
                                                                 ): TurtlePathBuilder[T] = {
    callbackOps
      .filter(_.module == "turtle")
      .foldLeft(TurtlePathBuilder[T]()) { (builder, op) =>
        val command = TurtleCommand[T](op.method, op.args.flatMap(toT[T]).toList)
        builder.handleStringCommand(command)
      }
  }

  private def turtleCallbackLibrary(): CallbackLibrary = {
    val methods =
      TurtleSingleton.allowedCommands().toList.map(name => name -> ignoreMethod).toMap
    CallbackLibrary("turtle", methods)
  }

  private def ignoreMethod(args: Vector[js.Any]): Unit = ()

  
   def parseTurtleCommands[T: Fractional](value: js.Any): List[TurtleCommand[T]] = {
    val arr = value.asInstanceOf[js.Array[js.Any]]
    arr.iterator.toList.map { raw =>
      val dyn = raw.asInstanceOf[js.Dynamic]
      val name = readStringField(dyn, TurtleFieldKeys.Name)
      val args = readArrayField(dyn, TurtleFieldKeys.Args).iterator.flatMap(toT[T]).toList
      TurtleCommand[T](name, args)
    }
  }

   def parseTurtleState[T: Fractional](value: js.Any): TurtleState[T] = {
    val dyn = value.asInstanceOf[js.Dynamic]
    TurtleState.initial[T](
      x = readNumberField[T](dyn, TurtleFieldKeys.X),
      y = readNumberField[T](dyn, TurtleFieldKeys.Y),
      headingDeg = readNumberField[T](dyn, TurtleFieldKeys.HeadingDeg),
      penDown = readBoolField(dyn, TurtleFieldKeys.PenDown),
      visible = readBoolField(dyn, TurtleFieldKeys.Visible)
    )
  }

   def parsePoint[T: Fractional](value: js.Any, fieldName: String = "point"): Point[T] = {
    val dyn = value.asInstanceOf[js.Dynamic]
    readPoint[T](dyn, TurtleFieldKeys.X, TurtleFieldKeys.Y, fieldName)
  }

   def parseSvgCommands[T: Fractional](value: js.Any): List[SvgPathBuilderCommand[T]] = {
    val arr = value.asInstanceOf[js.Array[js.Any]]
    arr.iterator.toList.flatMap { raw =>
      val dyn = raw.asInstanceOf[js.Dynamic]
      val kind = readStringField(dyn, TurtleFieldKeys.Kind)
      kind match {
        case "MoveAbs" =>
          Some(MoveAbs[T](readPoint[T](dyn, TurtleFieldKeys.X, TurtleFieldKeys.Y, kind)))
        case "LineAbs" =>
          Some(LineAbs[T](readPoint[T](dyn, TurtleFieldKeys.X, TurtleFieldKeys.Y, kind)))
        case "MoveRel" =>
          Some(MoveRel[T](Dimension[T](readNumberField[T](dyn, TurtleFieldKeys.Dx), readNumberField[T](dyn, TurtleFieldKeys.Dy))))
        case "ArcRel" =>
          Some(ArcRel[T](
            rx = readNumberField[T](dyn, TurtleFieldKeys.Rx),
            ry = readNumberField[T](dyn, TurtleFieldKeys.Ry),
            xAxisRotationDeg = readNumberField[T](dyn, TurtleFieldKeys.RotationDeg),
            largeArc = readBoolField(dyn, TurtleFieldKeys.LargeArc),
            sweep = readBoolField(dyn, TurtleFieldKeys.Sweep),
            d = Dimension[T](readNumberField[T](dyn, TurtleFieldKeys.Dx), readNumberField[T](dyn, TurtleFieldKeys.Dy))
          ))
        case "CenteredCircleControl" =>
          Some(CenteredCircleControl[T](readNumberField[T](dyn, TurtleFieldKeys.Radius)))
        case _ => None
      }
    }
  }

   def readNumberField[T: Fractional](dyn: js.Dynamic, fieldName: String): T =
    readNumberField[T](dyn, fieldName, fieldName)

  private def readNumberField[T: Fractional](dyn: js.Dynamic, fieldName: String, displayFieldName: String): T = {
    val raw = readRequiredField(dyn, fieldName, displayFieldName)
    toT[T](raw).getOrElse {
      throw new IllegalArgumentException(s"Expected numeric field '$displayFieldName', but got '${js.typeOf(raw)}'.")
    }
  }

   def readBoolField(dyn: js.Dynamic, fieldName: String): Boolean = {
    readBooleanField(dyn, fieldName)
  }

   def readPoint[T: Fractional](dyn: js.Dynamic, xField: String, yField: String, context: String): Point[T] =
    Point[T](
      readNumberField[T](dyn, xField, s"$context.$xField"),
      readNumberField[T](dyn, yField, s"$context.$yField")
    )

  private def readRequiredField(dyn: js.Dynamic, fieldName: String, displayFieldName: String): js.Any = {
    JsHelpers.readRequiredField(dyn, fieldName, displayFieldName)
  }

  private def toT[T: Fractional](value: js.Any): Option[T] = {
    val asDouble = js.typeOf(value) match {
      case "number" => Some(value.asInstanceOf[Double])
      case "string" => value.toString.toDoubleOption
      case "boolean" => Some(if value.asInstanceOf[Boolean] then 1d else 0d)
      case _ => value.toString.toDoubleOption
    }
    val numeric = summon[Fractional[T]]
    asDouble.flatMap(v => numeric.parseString(v.toString))
  }
}
