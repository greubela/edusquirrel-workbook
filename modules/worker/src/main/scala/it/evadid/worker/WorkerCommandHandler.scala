package it.evadid.worker

import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.distribution.clients.{ExecutionClient, ExecutionClientPool}
import it.evadid.distribution.commandTypes.WorkerCommands.*
import it.evadid.core.datastructures.vectorShapes.renderer.FlowchartGutter
import it.evadid.vm.parsing.source.SourceParsing
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
      // Every element with the tag, nested ones included, in document order.
      val document = SourceParsing.parse(request.xml, SourceLanguage.Snap).syntax.fold(message => throw IllegalArgumentException(message), identity)
      (document.root +: document.root.descendants).map(_.element).filter(_.tag == request.tag)
    }),
    flowchart.toLocalExecutionClient((request, _) => Future(FlowchartGutter.of(request.language, request.source))),
    programAnalysis.toLocalExecutionClient((request, _) => Future(it.evadid.vm.analysis.ProgramAnalysis.of(request.state, request.environment))),
    runPython.toLocalExecutionClient((request, logger) => PyodideCommandRuntime.run(request, logger)),
    javaExecutionEvidence.toLocalExecutionClient((request, _) =>
      JavaTurtleEvidenceJob.run(request, python => PyodideCommandRuntime.run(python, BasicLogger()))),
    calculateQrCode.toLocalExecutionClient((request, _) => Future {
      val code = QrCode.fromText(request.text, request.errorCorrection, request.version, request.mask)
      CalculateQrCodeResult(code, code.modules)
    })
  ))
}
