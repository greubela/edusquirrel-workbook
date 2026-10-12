package it.evadid.distribution.commandTypes

import it.evadid.distribution.command.ExecutionCommandFactory
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapXmlParser
import it.evadid.workbook.model.qr.{QrCode, QrErrorCorrection}
import upickle.default.ReadWriter

/** DOM-independent jobs supported by the workbook worker. */
object WorkerCommands {
  case class HealthRequest() derives ReadWriter
  case class HealthResult(service: String, commands: List[String]) derives ReadWriter
  case class ParseSnapXmlRequest(xml: String, tag: String) derives ReadWriter
  case class CalculateQrCodeRequest(text: String, errorCorrection: QrErrorCorrection = QrErrorCorrection.M,
                                    version: Option[Int] = None, mask: Option[Int] = None) derives ReadWriter
  case class CalculateQrCodeResult(code: QrCode, modules: Vector[Vector[Boolean]]) derives ReadWriter

  val health = ExecutionCommandFactory[HealthRequest, HealthResult]("worker.health")
  val parseSnapXml = ExecutionCommandFactory[ParseSnapXmlRequest, List[SnapXmlParser.Element]]("worker.parseSnapXml")
  val calculateQrCode = ExecutionCommandFactory[CalculateQrCodeRequest, CalculateQrCodeResult]("worker.calculateQrCode")
  val supportedCommands: Set[String] = Set(health.name, parseSnapXml.name, calculateQrCode.name)
}
