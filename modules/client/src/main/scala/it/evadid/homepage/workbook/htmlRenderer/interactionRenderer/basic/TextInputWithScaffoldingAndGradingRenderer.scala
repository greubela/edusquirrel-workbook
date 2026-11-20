package it.evadid.homepage.workbook.htmlRenderer.interactionRenderer.basic

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.webElements.editor.SimpleTextEditor
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.interactionElements.gpt.TextInputWithScaffoldingAndGrading


object TextInputWithScaffoldingAndGradingRenderer extends LineBasedRenderingFactory[TextInputWithScaffoldingAndGrading] {
  override protected def createRendering(element: TextInputWithScaffoldingAndGrading): AtomarLineRendering = {
    val variable = element.interactionVariable
    val answer = variable.createBoundStateWithUpdateLogic( TextInteraction.decideTextareaUpdateImportance, fullInfo.workbookContextInformation).toAirstreamVar
    def label(key: String) = laminarHelper.plaintextStringSignal(LanguageMapContentId(s"basic/$key"))
    val reporter = fullInfo.signals.reportersFor(element).grading
    val content = div(
      cls := "text-input-feedback",
      p(cls := "text-input-feedback__task", child.text <-- laminarHelper.plaintextStringSignal(element.instructionText)),
      SimpleTextEditor(answer).getDomElement().amend(aria.label <-- laminarHelper.plaintextStringSignal(element.instructionText)),
      Option.when(reporter.isGradingSupported || reporter.isScaffoldingSupported)(
        p(cls := "interaction-scaffolding__notice", child.text <-- label("gradingNotice")))
    )
    // basicLine appends the shared feedback panel (results and hints) and the status icons.
    AtomarLineRendering.basicLine(element, content)
  }
}
