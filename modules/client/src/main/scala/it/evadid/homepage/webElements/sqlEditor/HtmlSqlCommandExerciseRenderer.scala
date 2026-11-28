package it.evadid.homepage.webElements.sqlEditor

import it.evadid.homepage.control.model.FullInfo

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.sql.SqlCommandExercise
import it.evadid.workbook.interaction.sync.UpdateImportance

case object HtmlSqlCommandExerciseRenderer extends LineBasedRenderingFactory[SqlCommandExercise] {
  override protected def createRendering(fullInfo: FullInfo, exercise: SqlCommandExercise): AtomarLineRendering = {
    val state = exercise.interactionVariable.createBoundStateWithUpdateImportance(
      UpdateImportance.MAJOR).toAirstreamVar
    AtomarLineRendering.basicLine(fullInfo, exercise, div(
      button(typ := "button", "Open SQL editor", onClick --> (_ =>
        fullInfo.displayControl.setFullscreen(SqlEditor(fullInfo.loggerSystemInfo.uiAndDomLogger, state, exercise.databaseConfig, fullInfo.backendExecutor,
          laminarHelper(fullInfo).plaintextStringSignal(it.evadid.core.datastructures.language.LanguageMapContentId("blockeditor/loading")))))),
      pre(child.text <-- state.signal)
    ))
  }
}
