package it.evadid.homepage.webElements.editor.code.EvaEditor

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*
import it.evadid.homepage.webElements.editor.code.TurtleTaskEditorExtension
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.{TurtleDrawingPolicy, TurtleGraphic}

case class EvaEditorTurtle(
    override val state: Var[ProgrammingState],
    override val config: EvaEditorConfig,
    target: TurtleGraphic,
    override val onStateEdited: ProgrammingState => Unit = _ => (),
    editorExtensions: List[EvaEditorExtension] = Nil,
    comparisonPolicy: TurtleDrawingPolicy = TurtleDrawingPolicy.Strokes
) extends EvaEditor {

  override lazy val extensions: List[EvaEditorExtension] =
    new TurtleTaskEditorExtension(state, Some(target), comparisonPolicy) :: editorExtensions

  override def furtherDomElements(): List[Element] = Nil


}
