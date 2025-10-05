package interactionPlugins.blockEnvironment.firstIteration

import com.raquo.airstream.eventbus.EventBus
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import org.scalajs.dom
import org.scalajs.dom.{Event, MouseEvent, PointerEvent}
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement

class HtmlTurtleEditorArea(
  program: TurtleBlockProgram,
  dragContext: TurtleBlockDragContext
) extends HtmlWorkbookElement {

  import TurtlePathSegment.*

  private val stackSpacing = 18.0
  private val canvasPadding = 32.0

  private case class DropTarget(
    path: program.BlockPath,
    index: Int,
    area: TurtleRectangleArea,
    acceptTypes: Set[TurtleDataType]
  )

  private case class RenderedParameterSlot(
    connection: TurtleBlockConnection,
    relativeArea: TurtleRectangleArea,
    stack: StackLayout
  )

  private case class RenderedInside(
    connection: TurtleBlockConnection,
    relativeArea: TurtleRectangleArea,
    stack: StackLayout
  )

  private case class RenderedBlock(
    node: TurtleStructuredBlock,
    stackPath: program.BlockPath,
    indexInStack: Int,
    x: Double,
    y: Double,
    width: Double,
    height: Double,
    label: String,
    shape: TurtleBlockShape,
    inside: Option[RenderedInside],
    parameterSlots: List[RenderedParameterSlot]
  )

  private case class StackLayout(
    path: program.BlockPath,
    originX: Double,
    originY: Double,
    blocks: List[RenderedBlock],
    dropTargets: List[DropTarget],
    width: Double,
    height: Double
  )

  private case class ProgramLayout(
    rootStack: StackLayout,
    stackIndex: Map[program.BlockPath, StackLayout],
    dropTargets: List[DropTarget],
    width: Double,
    height: Double
  )

  private case class BlockLayoutResult(block: RenderedBlock, dropTargets: List[DropTarget])

  private case class PointerDragState(
    pointerId: Double,
    sourcePath: program.BlockPath,
    sourceIndex: Int,
    offsetX: Double,
    offsetY: Double,
    preview: StackLayout,
    pointerX: Double,
    pointerY: Double
  )

  private val highlightedTargetVar: Var[Option[DropTarget]] = Var(None)
  private val pointerDragStateVar: Var[Option[PointerDragState]] = Var(None)
  private val layoutVar: Var[Option[ProgramLayout]] = Var(None)

  private val svgElementVar: Var[Option[dom.svg.SVG]] = Var(None)

  private val pointerMoveBus = EventBus[PointerEvent]()
  private val pointerUpBus = EventBus[PointerEvent]()

  private def stackDropTypes: Set[TurtleDataType] = Set(TurtleDataType.Unit)

  private def layoutStack(
    blocks: List[TurtleStructuredBlock],
    path: program.BlockPath,
    originX: Double,
    originY: Double,
    widthHint: Double,
    allowPrepend: Boolean
  ): StackLayout = {
    val dropTargets = scala.collection.mutable.ListBuffer.empty[DropTarget]
    val renderedBlocks = scala.collection.mutable.ListBuffer.empty[RenderedBlock]
    var currentY = 0.0
    var maxWidth = widthHint

    if (blocks.isEmpty && allowPrepend) {
      val dropArea = TurtleRectangleArea(originX, originY, widthHint, 36.0)
      dropTargets += DropTarget(path, 0, dropArea, stackDropTypes)
      return StackLayout(path, originX, originY, Nil, dropTargets.toList, widthHint, 36.0)
    }

    blocks.zipWithIndex.foreach { case (node, idx) =>
      val layout = layoutBlock(node, path, idx, originX, originY, currentY)
      renderedBlocks += layout.block
      dropTargets ++= layout.dropTargets
      val blockBottom = currentY + layout.block.height
      val dropArea = TurtleRectangleArea(originX, originY + blockBottom + 4.0, math.max(widthHint, layout.block.width), 14.0)
      dropTargets += DropTarget(path, idx + 1, dropArea, stackDropTypes)
      currentY = blockBottom + stackSpacing
      maxWidth = math.max(maxWidth, layout.block.width)
    }

    if (allowPrepend && renderedBlocks.nonEmpty) {
      val firstBlock = renderedBlocks.head
      val dropArea = TurtleRectangleArea(originX, originY + firstBlock.y - 14.0, math.max(widthHint, firstBlock.width), 14.0)
      dropTargets += DropTarget(path, 0, dropArea, stackDropTypes)
    }

    val totalHeight =
      if (renderedBlocks.isEmpty) 0.0 else renderedBlocks.last.y + renderedBlocks.last.height

    StackLayout(
      path = path,
      originX = originX,
      originY = originY,
      blocks = renderedBlocks.toList,
      dropTargets = dropTargets.toList,
      width = math.max(widthHint, maxWidth),
      height = math.max(totalHeight, if (renderedBlocks.isEmpty) 36.0 else totalHeight)
    )
  }

  private def layoutBlock(
    node: TurtleStructuredBlock,
    stackPath: program.BlockPath,
    indexInStack: Int,
    stackOriginX: Double,
    stackOriginY: Double,
    relativeY: Double
  ): BlockLayoutResult = {
    val shape = node.block.definition.shape
    val absoluteX = stackOriginX
    val absoluteY = stackOriginY + relativeY

    val insideOpt = node.block.definition.connections.find(_.kind == TurtleConnectionKind.Enclosed).map { connection =>
      val area = connection.area
      val childPath = stackPath :+ IntoConnection(node.block.id, connection.id)
      val insideOriginX = absoluteX + area.x
      val insideOriginY = absoluteY + area.y
      val stackLayout = layoutStack(node.childrenFor(connection.id), childPath, insideOriginX, insideOriginY, widthHint = area.width, allowPrepend = true)
      val minHeight = math.max(area.height, 36.0)
      val effectiveHeight = math.max(minHeight, stackLayout.height)
      val relativeArea = area.withHeight(effectiveHeight)
      RenderedInside(connection, relativeArea, stackLayout)
    }

    val insideHeight = insideOpt.map(_.relativeArea.height).getOrElse(0.0)
    val blockHeight = shape.computeHeight(insideHeight)
    val blockWidth = shape.width

    val parameterSlots = node.block.definition.connections.collect {
      case connection if connection.kind == TurtleConnectionKind.Parameter =>
        val area = connection.area
        val childPath = stackPath :+ IntoConnection(node.block.id, connection.id)
        val absoluteArea = area.translate(absoluteX, absoluteY)
        val childLayoutOpt = node.childrenFor(connection.id).headOption.map { child =>
          val layout = layoutBlock(child, childPath, 0, absoluteArea.x, absoluteArea.y, 0.0)
          val offsetX = (area.width - layout.block.width) / 2.0
          val offsetY = (area.height - layout.block.height) / 2.0
          val adjustedBlock = layout.block.copy(x = offsetX, y = offsetY)
          val adjustedDropTargets = layout.dropTargets.map { target =>
            target.copy(area = target.area.translate(offsetX, offsetY))
          }
          (adjustedBlock, adjustedDropTargets)
        }
        val children = childLayoutOpt.map(_._1).toList
        val childDropTargets = childLayoutOpt.map(_._2).getOrElse(Nil)
        val slotDropTarget = DropTarget(childPath, 0, absoluteArea, connection.acceptTypes)
        val stack = StackLayout(childPath, absoluteArea.x, absoluteArea.y, children, childDropTargets :+ slotDropTarget, area.width, area.height)
        RenderedParameterSlot(connection, area, stack)
    }

    val block = RenderedBlock(
      node = node,
      stackPath = stackPath,
      indexInStack = indexInStack,
      x = 0.0,
      y = relativeY,
      width = blockWidth,
      height = blockHeight,
      label = node.block.label,
      shape = shape,
      inside = insideOpt,
      parameterSlots = parameterSlots
    )

    val dropTargets = insideOpt.map(_.stack.dropTargets).getOrElse(Nil) ++ parameterSlots.flatMap(_.stack.dropTargets)

    BlockLayoutResult(block, dropTargets)
  }

  private def collectStacks(stack: StackLayout): Map[program.BlockPath, StackLayout] = {
    val insideStacks = stack.blocks.flatMap(_.inside.map(_.stack))
    val parameterStacks = stack.blocks.flatMap(_.parameterSlots.map(_.stack))
    val nested = (insideStacks ++ parameterStacks).flatMap(collectStacks)
    nested.toMap + (stack.path -> stack)
  }

  private def computeLayout(blocks: List[TurtleStructuredBlock]): ProgramLayout = {
    val rootStack = layoutStack(blocks, program.rootPath, canvasPadding, canvasPadding, widthHint = 240.0, allowPrepend = false)
    val stackIndex = collectStacks(rootStack)
    val dropTargets = stackIndex.values.toList.flatMap(_.dropTargets)
    ProgramLayout(
      rootStack = rootStack,
      stackIndex = stackIndex,
      dropTargets = dropTargets,
      width = rootStack.width + canvasPadding * 2,
      height = rootStack.height + canvasPadding * 2
    )
  }

  private def pointerCoordinates(event: MouseEvent): Option[(Double, Double)] =
    svgElementVar.now().map { svgElem =>
      val rect = svgElem.getBoundingClientRect()
      (event.clientX - rect.left, event.clientY - rect.top)
    }

  private def payloadDataType(payload: TurtleDragPayload): Option[TurtleDataType] = payload match {
    case TurtleDragPayload.PaletteBlock(definition) => Some(definition.evaluatesTo)
    case TurtleDragPayload.EditorBlockGroup(blocks, _, _) => blocks.headOption.map(_.block.definition.evaluatesTo)
  }

  private def findBestTarget(x: Double, y: Double, payloadType: Option[TurtleDataType]): Option[DropTarget] = {
    val layoutOpt = layoutVar.now()
    val targets = layoutOpt.map(_.dropTargets).getOrElse(Nil)
    payloadType.flatMap { dataType =>
      targets
        .filter(_.acceptTypes.contains(dataType))
        .sortBy(target => target.area.distanceToArea(x, y))
        .headOption
    }
  }

  private def updateDropCandidate(x: Double, y: Double): Unit = {
    val candidate = dragContext.peek.flatMap(payload => findBestTarget(x, y, payloadDataType(payload)))
    highlightedTargetVar.set(candidate)
  }

  private def finalizeDrop(x: Double, y: Double): Unit = {
    highlightedTargetVar.now() match {
      case Some(target) =>
        dragContext.consumePayload() match {
          case Some(TurtleDragPayload.PaletteBlock(definition)) =>
            val blocks = TurtleBlockLibrary.instantiateWithCompanion(definition)
            program.insertBlocks(target.path, target.index, blocks)
          case Some(TurtleDragPayload.EditorBlockGroup(_, sourcePath, sourceIndex)) =>
            program.moveBlocks(sourcePath, sourceIndex, target.path, target.index)
          case None => ()
        }
      case None => dragContext.cancelDragIfNecessary()
    }
    highlightedTargetVar.set(None)
  }

  private def startPointerDrag(block: RenderedBlock, event: PointerEvent): Unit = {
    if (block.node.block.definition.key == TurtleBlockLibrary.whenProgramStarted.key) return
    pointerCoordinates(event).foreach { case (px, py) =>
      layoutVar.now().flatMap(_.stackIndex.get(block.stackPath)).foreach { stackLayout =>
        val blockTopLeftX = stackLayout.originX + block.x
        val blockTopLeftY = stackLayout.originY + block.y
        val offsetX = px - blockTopLeftX
        val offsetY = py - blockTopLeftY
        val previewBlocks = program.previewDetach(block.stackPath, block.indexInStack)
        dragContext.startEditorDrag(previewBlocks, block.stackPath, block.indexInStack)
        val previewLayout = layoutStack(previewBlocks, block.stackPath, blockTopLeftX, blockTopLeftY, widthHint = block.width, allowPrepend = false)
        pointerDragStateVar.set(Some(PointerDragState(event.pointerId, block.stackPath, block.indexInStack, offsetX, offsetY, previewLayout, px, py)))
        updateDropCandidate(px, py)
      }
    }
  }

  private def updatePointerDrag(event: PointerEvent): Unit = {
    pointerCoordinates(event).foreach { case (px, py) =>
      pointerDragStateVar.update {
        case Some(state) if state.pointerId == event.pointerId =>
          updateDropCandidate(px, py)
          Some(state.copy(pointerX = px, pointerY = py))
        case other => other
      }
    }
  }

  private def endPointerDrag(event: PointerEvent): Unit = {
    pointerCoordinates(event).foreach { case (px, py) =>
      pointerDragStateVar.now() match {
        case Some(state) if state.pointerId == event.pointerId =>
          finalizeDrop(px, py)
          pointerDragStateVar.set(None)
        case _ => ()
      }
    }
  }

  private def renderProgram(layout: ProgramLayout): Seq[L.SvgElement] = {
    val background = svg.rect(
      svg.x := "0",
      svg.y := "0",
      svg.width := layout.width.toString,
      svg.height := layout.height.toString,
      svg.fill := "#f5f7fb"
    )
    background +: renderStack(layout.rootStack, layout.rootStack.originX, layout.rootStack.originY)
  }

  private def renderStack(stack: StackLayout, parentOriginX: Double, parentOriginY: Double): Seq[L.SvgElement] =
    stack.blocks.map(block => renderBlock(stack, block, parentOriginX, parentOriginY))

  private def renderBlock(stack: StackLayout, block: RenderedBlock, parentOriginX: Double, parentOriginY: Double): L.SvgElement = {
    val absoluteX = stack.originX + block.x - parentOriginX
    val absoluteY = stack.originY + block.y - parentOriginY
    val insideElements = block.inside.toList.map { inside =>
      val background = svg.rect(
        svg.x := "0",
        svg.y := "0",
        svg.width := inside.relativeArea.width.toString,
        svg.height := inside.relativeArea.height.toString,
        svg.rx := "10",
        svg.ry := "10",
        svg.fill := "rgba(0, 0, 0, 0.08)"
      )
      svg.g(
        svg.transform := s"translate(${inside.relativeArea.x}, ${inside.relativeArea.y})",
        background,
        renderStack(inside.stack, inside.stack.originX, inside.stack.originY)
      )
    }

    val parameterElements = block.parameterSlots.flatMap { slot =>
      val base = svg.g(
        svg.rect(
          svg.x := slot.relativeArea.x.toString,
          svg.y := slot.relativeArea.y.toString,
          svg.width := slot.relativeArea.width.toString,
          svg.height := slot.relativeArea.height.toString,
          svg.rx := "12",
          svg.ry := "12",
          svg.fill := slot.connection.placeholderColor.getOrElse("rgba(0,0,0,0.15)"),
          svg.opacity := (if (slot.stack.blocks.nonEmpty) "0.25" else "0.35")
        ),
        svg.text(
          svg.x := (slot.relativeArea.x + slot.relativeArea.width / 2).toString,
          svg.y := (slot.relativeArea.y + slot.relativeArea.height / 2).toString,
          svg.fill := "#0c3359",
          svg.fontSize := "12",
          svg.textAnchor := "middle",
          svg.alignmentBaseline := "middle",
          slot.connection.placeholderLabel.getOrElse("")
        )
      )
      val childElement = slot.stack.blocks.headOption.map { childBlock =>
        svg.g(
          svg.transform := s"translate(${slot.relativeArea.x}, ${slot.relativeArea.y})",
          renderBlock(slot.stack, childBlock, slot.stack.originX, slot.stack.originY)
        )
      }
      childElement.toList :+ base
    }

    svg.g(
      svg.transform := s"translate($absoluteX, $absoluteY)",
      svg.style := "cursor: grab",
      onPointerDown --> { event =>
        if (event.button == 0) {
          event.preventDefault()
          startPointerDrag(block, event)
        }
      },
      onContextMenu.preventDefault --> (_ => program.removeBlock(block.node.block.id)),
      block.shape.render(block.label, block.height),
      insideElements,
      parameterElements
    )
  }

  private def renderHighlight(target: Option[DropTarget]): Option[L.SvgElement] = target.map { drop =>
    val area = drop.area
    svg.rect(
      svg.x := area.x.toString,
      svg.y := area.y.toString,
      svg.width := area.width.toString,
      svg.height := area.height.toString,
      svg.rx := "10",
      svg.ry := "10",
      svg.fill := "rgba(255, 204, 0, 0.35)",
      svg.stroke := "#ffcc00",
      svg.strokeDashArray := "8 4"
    )
  }

  private def renderPreview(state: Option[PointerDragState]): Option[L.SvgElement] = state.map { dragState =>
    val offsetX = dragState.pointerX - dragState.offsetX
    val offsetY = dragState.pointerY - dragState.offsetY
    svg.g(
      svg.opacity := "0.7",
      svg.transform := s"translate($offsetX, $offsetY)",
      renderStack(dragState.preview, dragState.preview.originX, dragState.preview.originY)
    )
  }

  private val layoutSignal = program.blocksSignal.map(computeLayout)

  layoutSignal.foreach(value => layoutVar.set(Some(value)))(unsafeWindowOwner)
  pointerMoveBus.events.foreach(updatePointerDrag)(unsafeWindowOwner)
  pointerUpBus.events.foreach(endPointerDrag)(unsafeWindowOwner)

  private val svgElement: L.SvgElement = {
    svg.svg(
      onMountCallback(ctx => svgElementVar.set(Some(ctx.thisNode.ref.asInstanceOf[dom.svg.SVG]))),
      svg.pointerEvents := "all",
      svg.width <-- layoutSignal.map(_.width.toString),
      svg.height <-- layoutSignal.map(_.height.toString),
      svg.viewBox <-- layoutSignal.map(layout => s"0 0 ${layout.width} ${layout.height}"),
      onPointerMove --> pointerMoveBus.writer,
      onPointerUp --> pointerUpBus.writer,
      onDragOver --> { event =>
        dragContext.peek.foreach { _ =>
          event.preventDefault()
          pointerCoordinates(event).foreach { case (px, py) => updateDropCandidate(px, py) }
        }
      },
      onDrop --> { event =>
        event.preventDefault()
        pointerCoordinates(event).foreach { case (px, py) => finalizeDrop(px, py) }
      },
      children <-- layoutSignal.map(renderProgram),
      child.maybe <-- highlightedTargetVar.signal.map(renderHighlight),
      child.maybe <-- pointerDragStateVar.signal.map(renderPreview)
    )
  }

  override def getDomElement(): L.Element = svgElement
}
