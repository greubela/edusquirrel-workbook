package it.evadid.core.datastructures.vectorShapes.blocks

import it.evadid.core.datastructures.geometry.{Bounds, Dimension, Point}
import it.evadid.core.datastructures.language.AppLanguage.{English, HumanLanguage, Python}
import it.evadid.core.datastructures.language.LanguageMap
import it.evadid.core.datastructures.vectorShapes.abstractions.AppShapeElement
import it.evadid.core.datastructures.vectorShapes.blocks.BlockArrangement.*
import it.evadid.core.datastructures.vectorShapes.svg.SvgPathBuilder
import it.evadid.vm.BeProgram
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.{BeIfElse, BeRepeatNr, BeSequence, BeWhile}
import it.evadid.vm.code.defining.{BeDefineFunction, BeDefineVariable}
import it.evadid.vm.code.errors.{BeExpressionUnparsable, BeExpressionUnsupported, BeSingleLineComment}
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.code.tree.{BeExpressionReference, BeExtensionPoint}
import it.evadid.vm.code.usage.{BeAssignVariable, BeFunctionCall, BeUseValue}
import it.evadid.vm.editing.{BeNodePath, BeSequenceSlot}
import it.evadid.vm.naming.NamingStyle
import it.evadid.vm.types.BeChildRole.{BodySequence, ConditionInControlStructure, ExpressionInSequence, FunctionParameter, ValueInAssignment}
import it.evadid.vm.types.BeScope.GlobalScope
import it.evadid.vm.types.{BeDataType, BeDataValueLiteral, BeDataValueUnit, BeUseValueReference}

/** How much of the control flow is drawn next to the blocks. */
enum ControlFlowDisplay derives upickle.default.ReadWriter {
  /** Expressions only, one below the other. */
  case Hidden
  /** Nested blocks with their control-flow backgrounds. */
  case Backgrounds
  /** Backgrounds plus the control-flow lines, branches and arrows. */
  case Full
}

final case class BlockView(placeholders: Boolean = true, controlFlow: ControlFlowDisplay = ControlFlowDisplay.Full)
  derives upickle.default.ReadWriter

object BlockView {
  /** Library templates: expressions only, without slots. */
  val library: BlockView = BlockView(placeholders = false, controlFlow = ControlFlowDisplay.Hidden)
}

/** Drag state colouring the program: blocks are muted and slots show where the dragged block may go. */
final case class BlockHighlight(dragging: Boolean = false, accepting: Set[BeSequenceSlot] = Set.empty,
                                destination: Option[BeSequenceSlot] = None)

/** Draws programs as nested blocks with control-flow columns, using the vector shape framework. */
object BeExpressionBlocks {

  /** One drawn node: its nested shape, the control-flow and expression parts of a single statement row,
    * and its control-flow rows with the expression that sets each row's height.
    */
  private final case class Block(element: AppShapeElement[Double], flow: Option[AppShapeElement[Double]],
                                 expression: Option[AppShapeElement[Double]],
                                 lines: List[(FlowLine, Option[AppShapeElement[Double]])])

  private final case class Context(rendering: BlockRenderingConfig, view: BlockView, highlight: BlockHighlight) {
    val paints: BlockPaints = rendering.paints
    val segment: Double = rendering.segmentSize
    def config(paint: BlockPaint, role: Option[BlockRole] = None): BlockElementConfig = BlockElementConfig(paint, rendering.font, role = role)
    def mutedOnDrag(paint: BlockPaint): BlockPaint = if highlight.dragging then paints.muted else paint
    def text(value: String, paint: BlockPaint): AppShapeElement[Double] = BlockElements.text(value, rendering, paint)
    def localized(map: LanguageMap[HumanLanguage]): String = map.getInLanguage(rendering.language)
  }

  def program(program: BeProgram, view: BlockView, rendering: BlockRenderingConfig = BlockRenderingConfig(),
              highlight: BlockHighlight = BlockHighlight()): AppShapeElement[Double] = {
    val context = Context(rendering, view, highlight)
    val block = node(program.fullProgram, BeNodePath.root, context)
    view.controlFlow match {
      case ControlFlowDisplay.Hidden =>
        BlockElements.composite(Column(rendering, false, Horizontal.Left), block.lines.flatMap(_._2), context.config(context.paints.control))
      case ControlFlowDisplay.Backgrounds => block.element
      case ControlFlowDisplay.Full =>
        val layout = FlowLayout(block.lines.map(_._1))
        val heights = layout.stacked.zip(block.lines).map { case ((line, stack), (_, expression)) =>
          ((line.shape :: stack).map(_.naturalSize(context.segment).height) ++ expression.map(BlockElements.size(_).height)).max
        }
        val overlay = layout.overlay(rendering, heights).toShape(rendering)
        // The overlay's first row starts one segment below its origin.
        val blocks = Bounds(Point(0.0, context.segment), BlockElements.size(block.element))
        BlockElements.composite(Fixed(List(blocks, Bounds(Point(0.0, 0.0), BlockElements.size(overlay)))),
          List(block.element, overlay), context.config(context.paints.control))
    }
  }

  /** A library template as offered for dragging: labelled control structures, other statements as blocks. */
  def template(statement: BeExpression, label: Option[LanguageMap[HumanLanguage]], rendering: BlockRenderingConfig = BlockRenderingConfig()): AppShapeElement[Double] =
    label match {
      case Some(text) =>
        val context = Context(rendering, BlockView.library, BlockHighlight())
        around(BlockContainer.Rectangle, context.text(context.localized(text), context.paints.text), context.paints.libraryLabel, None, context)
      case None => program(BeProgram(statement), BlockView.library, rendering)
    }

  private def around(container: BlockContainer, inner: AppShapeElement[Double], paint: BlockPaint, role: Option[BlockRole], context: Context): AppShapeElement[Double] = {
    val outline = BlockElements.path(container.draw(_, context.rendering), container.naturalSize, context.config(paint))
    BlockElements.composite(AroundChild(container, context.rendering), List(outline, inner), context.config(paint, role))
  }

  private def flowElement(shape: FlowShape, context: Context): AppShapeElement[Double] =
    BlockElements.path(shape.background(_, context.segment), shape.naturalSize(context.segment), context.config(context.paints.controlFlowBackground))

  /** A statement drawn as one row: control-flow column and expression. */
  private def single(shape: FlowShape, expression: AppShapeElement[Double], context: Context): Block = {
    val flow = flowElement(shape, context)
    Block(BlockElements.composite(Row(context.rendering, false, Horizontal.Left), List(flow, expression), context.config(context.paints.control)),
      Some(flow), Some(expression), List(FlowLine.Plain(shape) -> Some(expression)))
  }

  private def childrenWithSlots(expression: BeExpression, context: Context) =
    expression.structureInfo.getChildren(context.view.placeholders, GlobalScope()).distinct

  private def child(expression: BeExpression, path: BeNodePath, role: it.evadid.vm.types.BeChildRole, context: Context,
                    blockRole: BeNodePath => Option[BlockRole] = _ => None): Option[Block] =
    expression.structureInfo.getChildrenAsReference(GlobalScope()).collectFirst {
      case BeExpressionReference(info, value) if info.myRoleInParent == role => node(value, path / role, context, blockRole(path / role))
    }

  private def value(path: BeNodePath): Option[BlockRole] = Some(BlockRole.Value(path))

  /** `role` is how the editor treats the node: a statement of a sequence, an editable value, or neither. */
  private def node(expression: BeExpression, path: BeNodePath, context: Context, role: Option[BlockRole] = None): Block = expression match {
    case BeStartProgram(sequence) =>
      val body = sequence.map(node(_, path / BodySequence(0), context)).toList
      val column = BlockElements.composite(Column(context.rendering, false, Horizontal.Left), body.map(_.element), context.config(context.paints.control))
      val element = BlockElements.composite(PlainColumn,
        List(flowElement(FlowShape.ProgramStarter, context), column, flowElement(FlowShape.ProgramStopper, context)), context.config(context.paints.control))
      Block(element, None, None, (FlowLine.Plain(FlowShape.ProgramStarter) -> None) :: body.flatMap(_.lines) ++
        List(FlowLine.Plain(FlowShape.ProgramStopper) -> None))
    case sequence: BeSequence => this.sequence(sequence, path, context)
    case BeIfElse(_, _, _) =>
      val condition = conditionExpression(expression, path, ConditionInControlStructure, context)
      val thenBody = child(expression, path, BodySequence(0), context).toList
      val elseBody = child(expression, path, BodySequence(1), context).toList
      nested(List(FlowShape.IfElseSplit, FlowShape.IfElseCross, FlowShape.IfElseUnion), List(Some(condition), None, None),
        List(thenBody, elseBody), role, context,
        (FlowLine.Increase(FlowShape.IfElseSplit, FlowShape.Directed(true, false)) -> Some(condition)) ::
          thenBody.flatMap(_.lines) ++ ((FlowLine.Replace(FlowShape.IfElseCross, FlowShape.Directed(true, false)) -> None) ::
          elseBody.flatMap(_.lines)) :+ (FlowLine.Decrease(FlowShape.IfElseUnion) -> None))
    case BeWhile(_, _) => loop(expression, path, conditionExpression(expression, path, ConditionInControlStructure, context), role, context)
    case BeRepeatNr(_, _) =>
      val amount = child(expression, path, ExpressionInSequence(0), context, value).flatMap(_.expression)
        .getOrElse(throw IllegalArgumentException("A repetition needs an amount."))
      loop(expression, path, amount, role, context)
    case call: BeFunctionCall => single(FlowShape.FunctionCall, functionCall(call, path, context, role), context)
    case used: BeUseValue => single(FlowShape.Empty, useValue(used, context, role), context)
    case variable: BeDefineVariable => single(FlowShape.Empty, defineVariable(variable, context), context)
    case BeAssignVariable(target, _) =>
      val assigned = child(expression, path, ValueInAssignment, context, value).flatMap(_.expression).toList
      val arrow = BlockElements.path(bounds => FlowDecorationPaths[Double](context.segment).dataArrowLeft(bounds.centerPoint),
        Dimension(context.segment / 5.0 * 8.0, context.segment / 5.0 * 8.0), context.config(context.paints.activeDecoration))
      val content = BlockElements.composite(Row(context.rendering), defineVariable(target, context) :: arrow :: assigned, context.config(context.paints.control))
      single(FlowShape.Directed(true, true),
        around(BlockContainer.Unit, content, context.mutedOnDrag(context.paints.function), role, context), context)
    case definition: BeDefineFunction =>
      val source = definition.structureInfo.toStringInLanguage(Python, English).replaceAll("\n", "")
      single(FlowShape.Empty, around(BlockContainer.Rectangle, context.text(source, context.paints.text), context.paints.control,
        role, context), context)
    case BeSingleLineComment(comment) =>
      single(FlowShape.Directed(true, false), around(BlockContainer.Comment, context.text(context.localized(comment), context.paints.text),
        context.mutedOnDrag(context.paints.control), role, context), context)
    case unparsable: BeExpressionUnparsable =>
      single(FlowShape.Empty, errorBox(unparsable.originalSource, role, context), context)
    case unsupported: BeExpressionUnsupported =>
      single(FlowShape.Directed(true, false), errorBox(unsupported.originalSource, role, context), context)
    case other =>
      single(FlowShape.Directed(true, false), errorBox(other.getClass.getSimpleName, role, context), context)
  }

  private def errorBox(source: String, role: Option[BlockRole], context: Context): AppShapeElement[Double] =
    around(BlockContainer.Rectangle, context.text(source, context.paints.text), context.paints.error, role, context)

  private def loop(expression: BeExpression, path: BeNodePath, condition: AppShapeElement[Double], role: Option[BlockRole],
                   context: Context): Block = {
    val body = child(expression, path, BodySequence(0), context).toList
    nested(List(FlowShape.RepetitionSplit, FlowShape.RepetitionUnion), List(Some(condition), None), List(body), role, context,
      (FlowLine.Increase(FlowShape.RepetitionSplit, FlowShape.DownUp(false)) -> Some(condition)) :: body.flatMap(_.lines) ++
        List(FlowLine.Decrease(FlowShape.RepetitionUnion) -> None))
  }

  /** The expressions of a condition sequence, one below the other. */
  private def conditionExpression(expression: BeExpression, path: BeNodePath, role: it.evadid.vm.types.BeChildRole, context: Context): AppShapeElement[Double] = {
    val parts = child(expression, path, role, context).toList.flatMap(_.lines.flatMap(_._2))
    BlockElements.composite(Column(context.rendering, false, Horizontal.Left, Vertical.Top), parts, context.config(context.paints.control))
  }

  private def sequence(sequence: BeSequence, path: BeNodePath, context: Context): Block = {
    val children = childrenWithSlots(sequence, context).map {
      case BeExpressionReference(info, statement) =>
        node(statement, path / info.myRoleInParent, context, Some(BlockRole.Statement(path / info.myRoleInParent)))
      case BeExtensionPoint(_, info, dataType) => info.myRoleInParent match {
        case ExpressionInSequence(index) => placeholder(BeSequenceSlot(path, index), dataType, context)
        case other => throw IllegalArgumentException(s"Unexpected sequence slot $other.")
      }
    }.toList
    val rows = children.map(block => if block.flow.nonEmpty && block.expression.nonEmpty then SequenceRow.FlowAndExpression else SequenceRow.Whole)
    val elements = children.flatMap(block => (block.flow, block.expression) match {
      case (Some(flow), Some(expression)) => List(flow, expression)
      case _ => List(block.element)
    })
    Block(BlockElements.composite(SequenceRows(rows), elements, context.config(context.paints.control)), None, None, children.flatMap(_.lines))
  }

  private def placeholder(slot: BeSequenceSlot, dataType: BeDataType, context: Context): Block = {
    val highlight = context.highlight
    val paint =
      if !highlight.dragging then context.paints.mutedFunction
      else if highlight.destination.contains(slot) then context.paints.destination
      else if highlight.accepting.contains(slot) then context.paints.accepting
      else context.paints.error
    val outline = container(dataType)
    single(FlowShape.Directed(true, true), BlockElements.path(outline.draw(_, context.rendering),
      outline.naturalSize, context.config(paint, Some(BlockRole.Slot(slot)))), context)
  }

  private def container(dataType: BeDataType): BlockContainer = dataType match {
    case BeDataType.String => BlockContainer.Text
    case BeDataType.Numeric | BeDataType.Int | BeDataType.Error => BlockContainer.Numeric
    case BeDataType.Boolean => BlockContainer.Boolean
    case BeDataType.Date => BlockContainer.Date
    case BeDataType.Unit => BlockContainer.Unit
    case _ => BlockContainer.Duck
  }

  private def functionCall(call: BeFunctionCall, path: BeNodePath, context: Context, role: Option[BlockRole]): AppShapeElement[Double] = {
    val parameters = call.structureInfo.getChildrenAsReference(GlobalScope()).collect {
      case BeExpressionReference(info @ _, value) if info.myRoleInParent.isInstanceOf[FunctionParameter] =>
        info.myRoleInParent.asInstanceOf[FunctionParameter].nr -> node(value, path / info.myRoleInParent, context, this.value(path / info.myRoleInParent))
    }.sortBy(_._1).flatMap(_._2.expression).toList
    val name = context.text(context.localized(call.funcDef.functionTypeInfo.displayName.asLanguageMap(NamingStyle.SnakeCase)), context.paints.text)
    val position = call.funcDef.functionTypeInfo.displayNamePosition
    val content = BlockElements.composite(Row(context.rendering),
      parameters.take(position) ++ (name :: parameters.drop(position)), context.config(context.paints.control))
    val output = call.funcDef.outputs.map(output => container(output.variableType)).getOrElse(BlockContainer.Unit)
    around(output, content, context.mutedOnDrag(context.paints.function), role, context)
  }

  /** Literals show their own text, variables their name. */
  private def useValue(used: BeUseValue, context: Context, role: Option[BlockRole]): AppShapeElement[Double] = used.value match {
    case BeDataValueLiteral(text) =>
      around(BlockContainer.Literal, context.text(text, context.paints.text), context.paints.literal, role, context)
    case BeDataValueUnit() =>
      around(BlockContainer.Literal, context.text("", context.paints.text), context.paints.literal, role, context)
    case BeUseValueReference(reference) =>
      around(container(reference.variableType), context.text(context.localized(reference.name.asLanguageMap(NamingStyle.SnakeCase)),
        context.paints.text), context.paints.variableUse, role, context)
  }

  private def defineVariable(variable: BeDefineVariable, context: Context): AppShapeElement[Double] =
    around(container(variable.variableType), context.text(context.localized(variable.name.asLanguageMap(NamingStyle.SnakeCase)),
      context.paints.invertedText), context.paints.variableDefinition, None, context)

  /** A control structure: flow-change rows with optional expressions, separated by indented bodies. */
  private def nested(changes: List[FlowShape], expressions: List[Option[AppShapeElement[Double]]], bodies: List[List[Block]],
                     role: Option[BlockRole], context: Context, lines: List[(FlowLine, Option[AppShapeElement[Double]])]): Block = {
    val segment = context.segment
    val leftColumn = segment * 6
    val flowWidth = segment * 12
    val expressionSizes = expressions.map(_.map(BlockElements.size))
    val bodySizes = bodies.map(_.map(block => BlockElements.size(block.element)))
    val changeHeights = changes.zip(expressionSizes).map((shape, size) => (size.map(_.height).toList :+ shape.naturalSize(segment).height).max)
    val bodyHeights = bodySizes.map(_.map(_.height).sum)
    val sections = changeHeights.zip(bodyHeights)
    val tops = sections.scanLeft(0.0) { case (top, (change, body)) => top + change + body }
    val placedExpressions = expressions.zip(expressionSizes).zip(sections).zip(tops).collect {
      case (((Some(expression), Some(size)), (change, _)), top) => expression -> Bounds(Point(flowWidth, top + (change - size.height) / 2), size)
    }
    val placedBodies = bodies.zip(bodySizes).zip(sections).zip(tops).flatMap { case (((blocks, sizes), (change, _)), top) =>
      blocks.map(_.element).zip(sizes.zip(sizes.scanLeft(top + change)(_ + _.height)).map((size, y) => Bounds(Point(leftColumn, y), size)))
    }
    val content = placedBodies ++ placedExpressions
    val size = Fixed(content.map(_._2)).minimum(Nil).ensureWidth(flowWidth * 2).ensureHeight(changeHeights.sum + bodyHeights.sum)
    val background = BlockElements.path(bounds => nestedBackground(bounds, changeHeights, bodyHeights, leftColumn, flowWidth, segment),
      size, context.config(context.paints.controlFlowBackground))
    val element = BlockElements.composite(Fixed(Bounds(Point(0.0, 0.0), size) :: content.map(_._2)),
      background :: content.map(_._1), context.config(context.paints.control, role))
    Block(element, None, None, lines)
  }

  private def nestedBackground(bounds: Bounds[Double], changeHeights: List[Double], bodyHeights: List[Double],
                               leftColumn: Double, flowWidth: Double, segment: Double): SvgPathBuilder[Double] = {
    val extra = bounds.width - flowWidth
    val right = flowWidth - leftColumn
    val top = SvgPathBuilder(bounds.startPoint).addControlFlowConnector(segment).horizontalLineWithWidth(right).horizontalLineWithWidth(extra)
    val sections = changeHeights.zip(bodyHeights).foldLeft(top) { case (path, (change, body)) =>
      path.verticalLineWithHeight(change).horizontalLineWithWidth(-extra).addControlFlowConnector(-segment, true)
        .verticalLineWithHeight(body).addControlFlowConnector(segment).horizontalLineWithWidth(extra)
    }
    changeHeights.lastOption.fold(sections)(last => sections.verticalLineWithHeight(last).horizontalLineWithWidth(-extra)
      .horizontalLineWithWidth(-right).addControlFlowConnector(-segment, true).closePath())
  }
}
