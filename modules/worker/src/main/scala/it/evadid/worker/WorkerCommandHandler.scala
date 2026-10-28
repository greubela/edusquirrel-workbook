package it.evadid.worker

import it.evadid.distribution.clients.{ExecutionClient, ExecutionClientPool}
import it.evadid.distribution.commandTypes.WorkerCommands.*
import it.evadid.core.datastructures.vectorShapes.renderer.PythonFlowchartGutter
import it.evadid.workbook.elements.interactionElements.programming.state.snap.SnapXmlParser
import it.evadid.workbook.model.qr.QrCode
import it.evadid.util.logging.BasicLogger
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
    pythonFlowchart.toLocalExecutionClient((request, _) => Future {
      PythonFlowchartGutter.fromPython(request.source)
    }),
    runPython.toLocalExecutionClient((request, logger) => PyodideCommandRuntime.run(request, logger)),
    javaExecutionEvidence.toLocalExecutionClient((request, _) =>
      JavaTurtleEvidenceJob.run(request, python => PyodideCommandRuntime.run(python, BasicLogger()))),
    calculateQrCode.toLocalExecutionClient((request, _) => Future {
      val code = QrCode.fromText(request.text, request.errorCorrection, request.version, request.mask)
      CalculateQrCodeResult(code, code.modules)
    })
  ))
}
