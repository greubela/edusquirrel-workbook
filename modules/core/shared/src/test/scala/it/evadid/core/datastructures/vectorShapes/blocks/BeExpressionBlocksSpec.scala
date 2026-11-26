package it.evadid.core.datastructures.vectorShapes.blocks

import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage

import it.evadid.core.datastructures.geometry.Point
import it.evadid.core.datastructures.vectorShapes.FlowchartTestPlatform
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.AppElementRendered
import it.evadid.core.datastructures.vectorShapes.atomar.AppShapeDrawingRoutineElement
import it.evadid.vm.BeProgram
import it.evadid.vm.editing.{BeBlockLibrary, BeNodePath, BeProgramEditing}
import munit.FunSuite

class BeExpressionBlocksSpec extends FunSuite {
  override def beforeAll(): Unit = FlowchartTestPlatform.setup()
  override def afterAll(): Unit = FlowchartTestPlatform.cleanup()

  private val program = SourceParsing.parse("""import turtle
      |# draw
      |x = 3
      |for _ in range(4):
      |    forward(100)
      |    right(90)
      |while x < 5:
      |    x = x + 1
      |if x > 2:
      |    penup()
      |else:
      |    pendown()
      |""".stripMargin, SourceLanguage.Python).parsed().program

  private def leaves(node: AppElementRendered[Double]): List[AppElementRendered[Double]] =
    if node.children.isEmpty then List(node) else node.children.flatMap(leaves)

  private def laidOut(view: BlockView, highlight: BlockHighlight = BlockHighlight()) =
    BlockElements.layout(BeExpressionBlocks.program(program, view, highlight = highlight))

  test("every view lays out finite, non-empty drawings") {
    ControlFlowDisplay.values.foreach { display =>
      val rendered = laidOut(BlockView(controlFlow = display))
      assert(rendered.outerBounds.width > 0 && rendered.outerBounds.height > 0, display)
      assert(leaves(rendered).forall(leaf => leaf.myBounds.startX.isFinite && leaf.myBounds.startY.isFinite), display)
    }
  }

  test("slots, statements and values carry their editor roles") {
    val roles = BlockElements.roles(laidOut(BlockView())).map(_._1)
    assertEquals(roles.collect { case BlockRole.Slot(slot) => slot }.toSet, BeProgramEditing.slots(program).toSet)
    val statements = roles.collect { case BlockRole.Statement(path) => path }
    assert(statements.nonEmpty)
    assert(statements.forall(path => BeProgramEditing.find(program, path).nonEmpty), statements)
    val literals = roles.collect { case BlockRole.Value(path) => path }
    assert(literals.exists(path => BeProgramEditing.find(program, path).exists(_.toString.contains("BeDataValueLiteral(100)"))), literals)
    assert(BlockElements.roles(laidOut(BlockView(placeholders = false))).forall(!_._1.isInstanceOf[BlockRole.Slot]))
  }

  test("placeholders show where a dragged block may go") {
    val slots = BeProgramEditing.slots(program)
    val highlight = BlockHighlight(dragging = true, accepting = slots.take(2).toSet, destination = slots.headOption)
    val rendered = laidOut(BlockView(), highlight)
    def paintOf(role: BlockRole) = leaves(rendered).collectFirst {
      case leaf if leaf.elementConfig.asInstanceOf[BlockElementConfig].role.contains(role) => leaf.elementConfig.asInstanceOf[BlockElementConfig].paint
    }.get
    val paints = BlockRenderingConfig().paints
    assertEquals(paintOf(BlockRole.Slot(slots.head)), paints.destination)
    assertEquals(paintOf(BlockRole.Slot(slots(1))), paints.accepting)
    assertEquals(paintOf(BlockRole.Slot(slots.last)), paints.error)
    assertEquals(BlockElements.roles(laidOut(BlockView())).map(_._2), BlockElements.roles(rendered).map(_._2))
  }

  test("the control-flow overlay rows line up with the block rows") {
    val segment = BlockRenderingConfig().segmentSize
    val rendered = laidOut(BlockView())
    val blocks = rendered.children.head
    assertEquals(blocks.outerBounds.startPoint, Point(0.0, segment))
    val starterBackground = leaves(blocks).head
    assert(starterBackground.baseElement.isInstanceOf[AppShapeDrawingRoutineElement[?]])
    assertEquals(starterBackground.myBounds.startY, segment)
    assertEquals(starterBackground.myBounds.height, segment * 3)
  }

  test("library templates draw labelled control structures and plain commands") {
    BeBlockLibrary.turtle.templates.foreach { template =>
      val size = BlockElements.size(BeExpressionBlocks.template(template.statement, template.label))
      assert(size.width > 0 && size.height > 0, template)
    }
    assert(BlockElements.roles(BlockElements.layout(BeExpressionBlocks.program(BeProgram.empty, BlockView()))).nonEmpty)
    assert(BeProgramEditing.find(program, BeNodePath.root).nonEmpty)
  }
}
