package it.evadid.homepage.workbook.htmlRenderer.displayRenderer

import it.evadid.homepage.control.model.FullInfo

import it.evadid.distribution.commandTypes.SubmissionCommands
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.homepage.workbook.htmlRenderer.controlElements.HtmlSubmissionForm
import it.evadid.workbook.elements.display.WorkbookSubmissionElement

import scala.concurrent.ExecutionContext

/** Sends submissions without the session's backend token: a submission is never tied to an account. */
object HtmlWorkbookSubmissionRenderer extends LineBasedRenderingFactory[WorkbookSubmissionElement] {
  private given ExecutionContext = ExecutionContext.global

  override protected def createRendering(fullInfo: FullInfo, element: WorkbookSubmissionElement): AtomarLineRendering = {
    val logger = fullInfo.loggerSystemInfo.uiAndDomLogger
    val form = HtmlSubmissionForm(fullInfo, submitter => fullInfo.homepageInfoNow().toSubmissionBy(submitter, fullInfo.interactions.info), fullInfo.signals.user,
      event => SubmissionCommands.submitSessionCommand.sendCommandTo(fullInfo.defaults.backendExecutorWithCredentials(None),
        event, Some(logger)).map(_.result), logger)
    AtomarLineRendering.basicLine(fullInfo, element, form.getDomElement())
  }
}
