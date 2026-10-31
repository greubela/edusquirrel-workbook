package it.evadid.distribution.commandTypes

import it.evadid.distribution.command.ExecutionCommandFactory
import it.evadid.core.datastructures.vectorShapes.renderer.PythonFlowchartGutter
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapXmlParser
import it.evadid.workbook.model.qr.{QrCode, QrErrorCorrection}
import it.evadid.vm.simulation.java.{JavaTurtleExecutionEvidence, JavaTurtleRuntime, JavaTurtleTrace}
import upickle.default.*

/** DOM-independent jobs supported by the workbook worker. */
object WorkerCommands {
  case class HealthRequest() derives ReadWriter
  case class HealthResult(service: String, commands: List[String]) derives ReadWriter
  case class ParseSnapXmlRequest(xml: String, tag: String) derives ReadWriter
  case class PythonFlowchartRequest(source: String) derives ReadWriter
  case class CalculateQrCodeRequest(text: String, errorCorrection: QrErrorCorrection = QrErrorCorrection.M,
                                    version: Option[Int] = None, mask: Option[Int] = None) derives ReadWriter
  case class CalculateQrCodeResult(code: QrCode, modules: Vector[Vector[Boolean]]) derives ReadWriter
  case class RunPythonRequest(code: String, context: Map[String, ujson.Value] = Map.empty,
      callbacks: Map[String, List[String]] = Map.empty, packages: List[String] = Nil,
      resetGlobals: Boolean = true, captureStdout: Boolean = true, captureStderr: Boolean = true) derives ReadWriter
  case class PythonCallback(module: String, method: String, args: List[ujson.Value]) derives ReadWriter
  case class RunPythonResult(value: ujson.Value, stdout: String, stderr: String,
      callbackOps: List[PythonCallback]) derives ReadWriter
  case class JavaExecutionEvidenceRequest(source: String, method: Option[String] = None,
      arguments: Vector[JavaTurtleExecutionEvidence.Scalar] = Vector.empty,
      limits: JavaTurtleRuntime.Limits = JavaTurtleRuntime.Limits(),
      budget: JavaTurtleTrace.Budget = JavaTurtleTrace.Budget(), commandIndex: Option[Int] = None)

  object JavaExecutionEvidenceRequest {
    private val derived: ReadWriter[JavaExecutionEvidenceRequest] = macroRW

    private def integer(value: ujson.Value): Unit =
      require(value.numOpt.exists(_.isValidInt), "Expected an int32 value.")

    given ReadWriter[JavaExecutionEvidenceRequest] = readwriter[ujson.Value].bimap(
      request => writeJs(request)(using derived),
      json => {
        val fields = json.obj
        fields.get("commandIndex").filter(_ != ujson.Null).foreach(integer)
        List("limits" -> List("maxSteps", "maxCommands", "maxCallDepth", "maxBlockDepth"),
          "budget" -> List("maxEvents", "maxDependencies")).foreach { (field, integers) =>
          fields.get(field).foreach { value =>
            integers.foreach(name => value.obj.get(name).foreach(integer))
          }
        }
        fields.get("arguments").foreach(_.arr.foreach { argument =>
          argument("$type").str match {
            case "Int32" => integer(argument("value"))
            case "Bool" => require(argument("value").boolOpt.isDefined, "Expected a boolean value.")
            case "Float64" => require(argument("bits").strOpt.isDefined, "Expected double bits.")
            case _ => throw IllegalArgumentException("Invalid invocation argument type.")
          }
        })
        read[JavaExecutionEvidenceRequest](json)(using derived)
      })
  }

  lazy val health = ExecutionCommandFactory[HealthRequest, HealthResult]("worker.health")
  lazy val parseSnapXml = ExecutionCommandFactory[ParseSnapXmlRequest, List[SnapXmlParser.Element]]("worker.parseSnapXml")
  lazy val pythonFlowchart = ExecutionCommandFactory[PythonFlowchartRequest, PythonFlowchartGutter.Chart]("worker.pythonFlowchart")
  lazy val calculateQrCode = ExecutionCommandFactory[CalculateQrCodeRequest, CalculateQrCodeResult]("worker.calculateQrCode")
  lazy val runPython = ExecutionCommandFactory[RunPythonRequest, RunPythonResult]("worker.runPython",
    privacyFilter = Some { payload =>
      val fields = payload.objOpt
      val length = fields.flatMap(_.get("code")).flatMap(_.strOpt).filter(_ != null).map(_.length)
        .orElse(fields.filter(_.get("redacted").contains(ujson.Bool(true)))
          .flatMap(_.get("codeLength")).flatMap(_.numOpt).filter(n => n >= 0 && n <= Int.MaxValue && n % 1 == 0).map(_.toInt))
      ujson.Obj("redacted" -> true, "codeLength" -> length.fold[ujson.Value](ujson.Null)(ujson.Num(_)))
    })
  lazy val javaExecutionEvidence = ExecutionCommandFactory[JavaExecutionEvidenceRequest, JavaTurtleExecutionEvidence.Snapshot](
    "worker.javaExecutionEvidence", privacyFilter = Some { payload =>
      val fields = payload.objOpt
      val length = fields.flatMap(_.get("source")).flatMap(_.strOpt).filter(_ != null).map(_.length)
        .orElse(fields.filter(_.get("redacted").contains(ujson.Bool(true)))
          .flatMap(_.get("sourceLength")).flatMap(_.numOpt).filter(n => n >= 0 && n <= Int.MaxValue && n % 1 == 0).map(_.toInt))
      ujson.Obj("redacted" -> true, "sourceLength" -> length.fold[ujson.Value](ujson.Null)(ujson.Num(_)))
    })
  lazy val supportedCommands: Set[String] = Set(health.name, parseSnapXml.name, pythonFlowchart.name,
    calculateQrCode.name, runPython.name, javaExecutionEvidence.name)
}
