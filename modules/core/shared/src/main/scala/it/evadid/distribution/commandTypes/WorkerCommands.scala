package it.evadid.distribution.commandTypes

import it.evadid.distribution.command.ExecutionCommandFactory
import it.evadid.core.datastructures.vectorShapes.renderer.PythonFlowchartGutter
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapXmlParser
import it.evadid.workbook.model.qr.{QrCode, QrErrorCorrection}
import upickle.default.ReadWriter

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
  lazy val supportedCommands: Set[String] = Set(health.name, parseSnapXml.name, pythonFlowchart.name, calculateQrCode.name, runPython.name)
}
