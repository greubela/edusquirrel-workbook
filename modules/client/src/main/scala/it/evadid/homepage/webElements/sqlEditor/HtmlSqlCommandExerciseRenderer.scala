package it.evadid.homepage.webElements.sqlEditor

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.state.StateHelper.StateBasedVar
import it.evadid.homepage.workbook.htmlRenderer.HtmlRenderFactory.LineBasedRenderingFactory
import it.evadid.homepage.workbook.htmlRenderer.atomarLineRenderings.AtomarLineRendering
import it.evadid.workbook.elements.interactionElements.sql.SqlCommandExercise
import it.evadid.workbook.interaction.sync.UpdateImportance

case object HtmlSqlCommandExerciseRenderer extends LineBasedRenderingFactory[SqlCommandExercise] {
  override protected def createRendering(exercise: SqlCommandExercise): AtomarLineRendering = {
    val state = exercise.interactionVariable.createBoundStateWithUpdateImportance(
      fullInfo.syncControl, UpdateImportance.MAJOR).toAirstreamVar
    AtomarLineRendering.basicLine(exercise, div(
      button(typ := "button", "Open SQL editor", onClick --> (_ =>
        fullInfo.displayControl.setFullscreen(SqlEditor(state, exercise.databaseConfig, fullInfo.defaults.backendExecutor)))),
      pre(child.text <-- state.signal)
    ))
  }
}
