package it.evadid.worker

import it.evadid.distribution.clients.{ExecutionClient, ExecutionClientPool}
import it.evadid.distribution.commandTypes.WorkerCommands.*
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapXmlParser
import it.evadid.workbook.model.qr.QrCode
import scala.concurrent.{ExecutionContext, Future}

/** Runs only inside worker.js; jobs never access DOM or server credentials. */
object WorkerCommandHandler {
  private given ExecutionContext = ExecutionContext.global
  val executor: ExecutionClient = ExecutionClientPool(List(
    health.toLocalExecutionClient((_, _) => Future.successful(HealthResult("edusquirrel-worker", supportedCommands.toList.sorted))),
    parseSnapXml.toLocalExecutionClient((request, _) => Future {
      require(request.tag.matches("[A-Za-z_][A-Za-z0-9_.:-]*"), "Invalid Snap XML tag")
      SnapXmlParser.elements(request.xml, request.tag)
    }),
    runPython.toLocalExecutionClient((request, logger) => PyodideCommandRuntime.run(request, logger)),
    calculateQrCode.toLocalExecutionClient((request, _) => Future {
      val code = QrCode.fromText(request.text, request.errorCorrection, request.version, request.mask)
      CalculateQrCodeResult(code, code.modules)
    })
  ))
}
