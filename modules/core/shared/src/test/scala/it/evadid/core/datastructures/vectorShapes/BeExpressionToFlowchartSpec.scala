package it.evadid.core.datastructures.vectorShapes

import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement.AppElementRendered
import it.evadid.core.datastructures.vectorShapes.atomar.{AppShapeDrawingRoutineElement, AppShapeTextElement}
import it.evadid.core.datastructures.vectorShapes.config.AppShapeRenderingConfig
import it.evadid.core.datastructures.vectorShapes.renderer.{BeExpressionToFlowchart, SvgViewport}
import it.evadid.core.datastructures.vectorShapes.renderer.BeExpressionToFlowchart.*
import it.evadid.core.datastructures.vectorShapes.svg.SvgPath.BuilderBasedSvgPath
import it.evadid.util.logging.BasicLogger
import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.*
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.others.{BeReturn, BeStartProgram}
import it.evadid.vm.code.usage.BeUseValue
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral}
import munit.FunSuite

class BeExpressionToFlowchartSpec extends FunSuite {
  override def beforeAll(): Unit = FlowchartTestPlatform.setup()
  override def afterAll(): Unit = FlowchartTestPlatform.cleanup()

  private def step(name: String): BeExpression = BeUseValue(BeDataValueLiteral(name), None)
  private def body(expressions: BeExpression*): BeSequence = BeSequence.optionalBody(expressions.toList)
  private val condition = BeSequence.conditionalBody(List(step("True")))
  private def outgoing(chart: Flowchart, id: Int): List[Edge] = chart.edges.filter(_.from == id)
  private def node(chart: Flowchart, text: String): Node = chart.nodes.find(_.label == text).get
  private def assertConnected(chart: Flowchart): Unit = {
    val ids = chart.nodes.map(_.id).toSet
    assertEquals(ids.size, chart.nodes.size)
    assert(chart.edges.forall(e => ids(e.from) && ids(e.to)))
    assertEquals(outgoing(chart, chart.endId), Nil)
  }

  test("empty programs connect Start directly to End") {
    for (expression <- List(BeExpression.pass, BeStartProgram(), BeStartProgram(BeExpression.pass))) {
      val chart = build(expression)
      assertEquals(chart.nodes.map(_.label).toSet, Set("Start", "End"))
      assertEquals(chart.edges, List(Edge(chart.startId, chart.endId)))
      assertConnected(chart)
    }
  }

  test("nested sequences preserve execution order without extra control nodes") {
    val chart = build(BeStartProgram(body(step("a"), body(step("b")), step("c"))))
    val labels = chart.nodes.map(n => n.id -> n.label).toMap
    var current = chart.startId
    val ordered = scala.collection.mutable.ListBuffer(labels(current))
    while (current != chart.endId) {
      current = outgoing(chart, current).head.to
      ordered += labels(current)
    }
    assertEquals(ordered.toList, List("Start", "a", "b", "c", "End"))
  }

  test("if/else branches join at the following statement, including empty bodies") {
    val chart = build(body(BeIfElse(condition, body(step("yes")), body(step("no"))), step("after")))
    val decision = chart.nodes.find(_.kind == NodeKind.Decision).get
    assertEquals(outgoing(chart, decision.id).map(e => e.label.get -> chart.nodes.find(_.id == e.to).get.label).toMap,
      Map("Yes" -> "yes", "No" -> "no"))
    for (label <- List("yes", "no")) assertEquals(outgoing(chart, node(chart, label).id).head.to, node(chart, "after").id)
    val empty = build(BeIfElse(condition, BeExpression.pass, BeExpression.pass))
    assertEquals(outgoing(empty, empty.nodes.find(_.kind == NodeKind.Decision).get.id).map(_.to), List(empty.endId, empty.endId))
  }

  test("while loops test before entering and return to the condition") {
    for (loopBody <- List(body(step("work")), BeExpression.pass)) {
      val chart = build(body(BeWhile(condition, loopBody), step("after")))
      val decision = chart.nodes.find(_.kind == NodeKind.Decision).get
      assertEquals(outgoing(chart, decision.id).find(_.label.contains("No")).get.to, node(chart, "after").id)
      assert(chart.edges.exists(e => e.to == decision.id && e.backEdge))
      assertConnected(chart)
      chart.toShape().renderWithMinimumDimension(AppShapeRenderingConfig.defaultDouble)
    }
  }

  test("counted repetition has initialization, a test and an update") {
    val chart = build(BeRepeatNr(3, body(step("work"))))
    val decision = chart.nodes.find(_.kind == NodeKind.Decision).get
    val init = chart.nodes.find(_.label.endsWith(" = 3")).get
    val update = chart.nodes.find(_.label.endsWith(" -= 1")).get
    assertEquals(outgoing(chart, chart.startId).head.to, init.id)
    assertEquals(outgoing(chart, init.id).head.to, decision.id)
    assertEquals(outgoing(chart, node(chart, "work").id).head.to, update.id)
    assertEquals(outgoing(chart, update.id), List(Edge(update.id, decision.id, backEdge = true)))
    assertEquals(outgoing(chart, decision.id).find(_.label.contains("No")).get.to, chart.endId)
  }

  test("inclusive for loops initialize and increment their own variable") {
    val variable = BeDefineVariable(BeEntityName.fromUniversalNameInParts("i"), BeDataType.Int)
    val chart = build(BeFor(variable, step("1"), step("3"), body(step("work"))))
    assert(chart.nodes.exists(_.label == "i <= 3"))
    assert(chart.nodes.exists(_.label == "i = 1"))
    assert(chart.nodes.exists(_.label == "i += 1"))
    assertEquals(chart.edges.count(_.backEdge), 1)
  }

  test("return goes to End and unreachable fall-through statements are omitted") {
    val returned = BeReturn(Some(step("42")))
    val chart = build(body(returned, step("unreachable")))
    assert(!chart.nodes.exists(_.label == "unreachable"))
    val returnNode = chart.nodes.find(_.expression.contains(returned)).get
    assertEquals(outgoing(chart, returnNode.id), List(Edge(returnNode.id, chart.endId)))
    val branches = build(body(BeIfElse(condition, body(returned), body(returned)), step("unreachable")))
    assert(!branches.nodes.exists(_.label == "unreachable"))
    assertEquals(branches.nodes.count(_.expression.contains(returned)), 2)
  }

  test("nested loops retain separate counters and return exits all loops") {
    val expression = BeRepeatNr(2, body(BeRepeatNr(3, body(step("work"))), BeReturn(None)))
    val chart = build(expression)
    assertEquals(chart.nodes.filter(_.label.endsWith(" > 0")).map(_.label).distinct.size, 2)
    assertConnected(chart)
    chart.toShape().renderWithMinimumDimension(AppShapeRenderingConfig.defaultDouble)
  }

  test("parsed programs use the new atomic shapes with finite paths and translated bounds") {
    val expression = BeProgram.fromPythonString("x = 1\nwhile x < 3:\n    if x == 2:\n        x = x + 1\n    else:\n        x = x + 2\n").fullProgram
    val chart = build(expression)
    assert(chart.nodes.count(_.kind == NodeKind.Decision) >= 2)
    val shape = BeExpressionToFlowchart(expression)
    val config = AppShapeRenderingConfig.defaultDouble
    val measured = shape.renderWithMinimumDimension(config)
    val offset = Point(100.0, 200.0)
    val rendered = shape.renderComposition(config, Bounds(offset, measured.outerBounds.dimension))
    assertEquals(rendered.outerBounds.startPoint, offset)
    val viewport = SvgViewport.boundsFor(rendered)
    assert(viewport.width.isFinite && viewport.height.isFinite && viewport.width > 0 && viewport.height > 0)
    def visit(node: AppElementRendered[Double]): Unit = {
      assert(node.myBounds.startX >= rendered.myBounds.startX)
      assert(node.myBounds.startY >= rendered.myBounds.startY)
      assert(node.myBounds.endX <= rendered.myBounds.endX + 0.001)
      assert(node.myBounds.endY <= rendered.myBounds.endY + 0.001)
      node.baseElement match {
        case drawing: AppShapeDrawingRoutineElement[Double] =>
          val path = drawing.renderPath(BasicLogger(), node.myBounds).asInstanceOf[BuilderBasedSvgPath[Double]]
          assert(!path.svgPathDString.contains("NaN") && !path.svgPathDString.contains("Infinity"))
          assert(path.svgPathDString.contains("M"))
          val painted = path.pathBuilder.bounds
          assert(painted.startX >= rendered.myBounds.startX && painted.startY >= rendered.myBounds.startY)
          assert(painted.endX <= rendered.myBounds.endX && painted.endY <= rendered.myBounds.endY)
        case _: AppShapeTextElement[Double] =>
        case _ => assert(node.children.nonEmpty)
      }
      node.children.foreach(visit)
    }
    visit(rendered)
    val text = rendered.children.collect { case n if n.baseElement.isInstanceOf[AppShapeTextElement[?]] => n.baseElement.asInstanceOf[AppShapeTextElement[Double]].text }
    assert(text.contains("Start") && text.contains("End") && text.contains("Yes") && text.contains("No"))
  }

  test("function declarations are process nodes and do not execute their return bodies") {
    val definition = BeDefineFunction(Nil, None, body(BeReturn(Some(step("1")))),
      BeDefineFunction.functionInfo(BeEntityName.fromUniversalNameInParts("f")))
    val following = step("after")
    val chart = build(body(definition, following))
    assert(chart.nodes.exists(_.label.startsWith("def f")))
    assert(!chart.nodes.exists(_.expression.exists(_.isInstanceOf[BeReturn])))
    assertEquals(outgoing(chart, chart.nodes.find(_.expression.contains(definition)).get.id).head.to,
      chart.nodes.find(_.expression.contains(following)).get.id)
    assertConnected(chart)
  }

  test("custom labels are supported without executing expressions") {
    val expression = step("work")
    val chart = build(expression, _ => "Custom <label> & text")
    assertEquals(chart.nodes.find(_.kind == NodeKind.Process).get.label, "Custom <label> & text")
  }
}
