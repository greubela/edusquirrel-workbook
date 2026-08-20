package it.evadid.homepage.webElements.editor.code.SnapEditor

import it.evadid.core.datastructures.state.async.AsyncData
import it.evadid.homepage.workbook.legacy.interactionPlugins.turtleStitchPlugin.TurtleStitchWorkerFacade
import it.evadid.workbook.elements.interactionElements.programming.ProgrammingExerciseState
import todomove.datastructures.web.file.FullImage

/**
 * Shared Run → TurtleStitchWorker.simulateGreenFlag → final stage PNG pipeline
 * used by the workbook-page Run card (instant snapshot, not live animation).
 *
 * Fullscreen Execute uses the live IDE green-flag + stage mirror instead
 * (`SnapCodeEditorImpl.runGreenFlagOnStage`).
 */
object SnapTurtleStage {

  def run(state: ProgrammingExerciseState): AsyncData[Nothing, FullImage] =
    TurtleStitchWorkerFacade.getExecutedStageSnapshotDataSrc(state.snapXml)
}
