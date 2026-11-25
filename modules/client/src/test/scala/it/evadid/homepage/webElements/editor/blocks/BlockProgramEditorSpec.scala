package it.evadid.homepage.webElements.editor.blocks

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.language.AppLanguage.{BlockDisplay, Python}
import it.evadid.core.datastructures.vectorShapes.blocks.{BeExpressionBlocks, BlockElements, BlockRole, BlockView}
import it.evadid.homepage.webElements.editor.code.EditorAppearance
import it.evadid.homepage.webElements.editor.code.EditorDomSuite
import it.evadid.vm.BeProgram
import it.evadid.vm.code.controlStructures.BeRepeatNr
import it.evadid.vm.conversion.pythonexpression.PythonToExpression
import it.evadid.vm.editing.{BeBlockLibrary, BeNodePath, BeProgramEditing, BeSequenceSlot}
import it.evadid.vm.types.BeChildRole.{BodySequence, ExpressionInSequence, FunctionParameter}
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingState, ProgrammingStateBeExpression, ProgrammingStateRaw}
import org.scalajs.dom

import scala.scalajs.js

class BlockProgramEditorSpec extends EditorDomSuite {
  override def beforeAll(): Unit = {
    super.beforeAll()
    val context = js.Dynamic.literal(font = "", measureText = ((text: String) => js.Dynamic.literal(
      width = text.length * 7.0, actualBoundingBoxAscent = 10.0, actualBoundingBoxDescent = 2.0)): js.Function1[String, js.Dynamic])
    js.Dynamic.global.window.HTMLCanvasElement.prototype.getContext = ((_: js.Any) => context): js.Function1[js.Any, js.Dynamic]
  }

  private val labels = BlockEditorLabels.fromContent(id => Val(id.entryKey))
  private val main = BeNodePath.root / BodySequence(0)
  private val program = PythonToExpression.program("forward(100)\nfor _ in range(4):\n    right(90)\n")
  private def roles(of: BeProgram) = BlockElements.roles(BlockElements.layout(BeExpressionBlocks.program(of, BlockView())))
  private def centerOf(role: BlockRole) = roles(program).collectFirst { case (`role`, bounds) => bounds.centerPoint }.get

  test("a press picks the innermost statement and value under the pointer") {
    val distance = main / ExpressionInSequence(0) / FunctionParameter(0)
    val (source, literal) = BlockDragging.pressAt(program, roles(program), centerOf(BlockRole.Value(distance)))
    assertEquals(literal, Some(distance))
    assertEquals(source.collect { case DragSource.Moving(path, _) => path }, Some(main / ExpressionInSequence(0)))
    val nested = main / ExpressionInSequence(1) / BodySequence(0) / ExpressionInSequence(0)
    val (inner, _) = BlockDragging.pressAt(program, roles(program), centerOf(BlockRole.Statement(nested)))
    assertEquals(inner.collect { case DragSource.Moving(path, _) => path }, Some(nested))
  }

  test("a dragged loop accepts every slot outside itself and drops at the nearest one") {
    val loopPath = main / ExpressionInSequence(1)
    val loop = BeProgramEditing.find(program, loopPath).get
    val accepted = BlockDragging.accepted(program, DragSource.Moving(loopPath, loop))
    assert(accepted.contains(BeSequenceSlot(main, 0)))
    assert(!accepted.exists(slot => loopPath.contains(slot.sequence)), accepted)
    val target = BlockDragging.nearest(roles(program), accepted, centerOf(BlockRole.Slot(BeSequenceSlot(main, 0))))
    assertEquals(target, Some(BeSequenceSlot(main, 0)))
    val moved = BlockDragging.drop(program, DragSource.Moving(loopPath, loop), target.get).toOption.get
    assert(BeProgramEditing.find(moved, main / ExpressionInSequence(0)).exists(_.isInstanceOf[BeRepeatNr]))
    val discarded = BlockDragging.discard(program, DragSource.Moving(loopPath, loop)).toOption.get
    assertEquals(BeProgramEditing.slots(discarded), Seq(BeSequenceSlot(main, 0), BeSequenceSlot(main, 1)))
  }

  test("a press only becomes a drag past the threshold and only with a source") {
    val start = Point(10.0, 10.0)
    val press = Press(Some(DragSource.Template(program.fullProgram)), None, start, start, false, None)
    assert(!press.movedTo(Point(12.0, 12.0), None).active)
    assert(press.movedTo(Point(20.0, 10.0), None).active)
    assert(!press.copy(source = None).movedTo(Point(40.0, 10.0), None).active)
  }

  test("the Eva tab edits the central answer's expression representation") {
    val tab = BlockProgramEditor.forEvaTab()
    assertEquals(tab.language, BlockDisplay)
    val converted = tab.convert(ProgrammingStateRaw(Python, "forward(100)\n"), it.evadid.vm.conversion.dependencies.ProgrammingEnvironment.all).finalState.get
    assertEquals(converted.associatedLanguage, BlockDisplay)
    val state = Var[ProgrammingState](converted)
    val unmount = mount(tab.create(state, it.evadid.homepage.webElements.editor.code.EvaTestContext(
      it.evadid.homepage.webElements.editor.code.EvaEditor.EvaEditorLabels.fromContent(id => Val(id.entryKey)))).getDomElement())
    try {
      def statements = dom.document.querySelectorAll(".block-editor__drawing [data-block-role=statement]").length
      assertEquals(statements, 1)
      assertEquals(dom.document.querySelectorAll(".block-editor__template").length, BeBlockLibrary.turtle.templates.size)
      // Keyboard alternative to dragging appends the focused template.
      val template = dom.document.querySelector(".block-editor__template")
      template.dispatchEvent(js.Dynamic.newInstance(js.Dynamic.global.window.KeyboardEvent)("keydown",
        js.Dynamic.literal(key = "Enter", bubbles = true)).asInstanceOf[dom.Event])
      val appended = state.now().asInstanceOf[ProgrammingStateBeExpression]
      assert(appended.toPython.code.split("forward\\(100\\)").length >= 3, appended.toPython.code)
      assertEquals(statements, 2)
      // Every change of the central answer is reflected, whatever its origin.
      state.set(ProgrammingStateRaw(Python, "left(1)\nleft(2)\nleft(3)\n").toBeExpression)
      assertEquals(statements, 3)
    } finally unmount()
  }
}
