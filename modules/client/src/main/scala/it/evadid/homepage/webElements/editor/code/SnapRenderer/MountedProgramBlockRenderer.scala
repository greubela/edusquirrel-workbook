package it.evadid.homepage.webElements.editor.code.SnapRenderer

import com.raquo.airstream.state.Var
import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.homepage.webElements.editor.code.SnapRenderer.MountedProgramBlockRenderer.{BlockShape, RenderLine}
import it.evadid.vm.BeProgram
import it.evadid.vm.code.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.naming.CodeRepresentationConfig
import org.scalajs.dom
import org.scalajs.dom.CanvasRenderingContext2D
import org.scalajs.dom.html.Canvas

object MountedProgramBlockRenderer {

  private enum BlockShape:
    case Hat, Command, Reporter

  private final case class RenderLine(label: String, depth: Int, shape: BlockShape)

}

final class MountedProgramBlockRenderer() extends BeProgramSnapRenderer {


  def mount(owner: com.raquo.airstream.ownership.Owner): Unit = {
  }

  private var frameHandle: Int = 0
  private var destroyed = false
  private var lastCssWidth = 0.0
  private var lastDpr = 0.0
  private var dirty = true

  def createContext(canvas: Canvas): CanvasRenderingContext2D = canvas.getContext("2d").asInstanceOf[CanvasRenderingContext2D]

  private def resizeCanvasIfNeeded(program: BeProgram, context: CanvasRenderingContext2D, canvas: Canvas, config: SnapCodeEditorConfig): Boolean = {
    val rect = canvas.getBoundingClientRect()
    // The drawing coordinate system must match the displayed width. Keeping
    // a 900px backing coordinate system inside a narrower workbook card made
    // both blocks and text appear unexpectedly tiny.
    val cssWidth = if (rect.width > 0) rect.width else config.CanvasWidth.toDouble
    val dpr = math.max(1.0, dom.window.devicePixelRatio)
    val changed = cssWidth != lastCssWidth || dpr != lastDpr || canvas.width == 0 || canvas.height == 0
    if (changed) {
      canvas.width = (cssWidth * dpr).round.toInt
      canvas.height = (config.CanvasHeight * dpr).round.toInt
      canvas.style.width = "100%"
      canvas.style.height = s"${config.CanvasHeight}px"
      context.setTransform(dpr, 0, 0, dpr, 0, 0)
      lastCssWidth = cssWidth
      lastDpr = dpr
    }
    changed
  }

  private def drawProgram(program: BeProgram, context: CanvasRenderingContext2D, canvas: Canvas, config: SnapCodeEditorConfig): Unit = {
    context.clearRect(0, 0, lastCssWidth, config.CanvasHeight)
    context.fillStyle = config.ColorWorkspace
    context.fillRect(0, 0, lastCssWidth, config.CanvasHeight)

    val lines = expressionToLines(config, program.fullProgram, 0)
    if (lines.isEmpty) drawEmptyProgram(program, context, canvas, config)
    else {
      var y = config.Padding
      lines.foreach { line =>
        y += drawSnapBlock(program, context, canvas, config, line, y) + config.BlockGap
      }
    }
  }

  private def drawEmptyProgram(program: BeProgram, context: CanvasRenderingContext2D, canvas: Canvas, config: SnapCodeEditorConfig): Unit = {
    context.font = "14px sans-serif"
    context.fillStyle = config.ColorEmpty
    context.fillText("No blocks yet", config.Padding, config.Padding + 20)
  }

  /** Ask Snap! to lay out and rasterize the actual Morph. Scala.js only owns
   * the destination canvas, positioning and redraw lifecycle.
   */
  private def drawSnapBlock(program: BeProgram, context: CanvasRenderingContext2D, canvas: Canvas, config: SnapCodeEditorConfig, line: RenderLine, y: Double): Double = {
    val x = config.Padding + line.depth * config.Indent
    val block: BlockMorph = line.shape match {
      case BlockShape.Hat => new HatBlockMorph()
      case BlockShape.Command => new CommandBlockMorph()
      case BlockShape.Reporter => new ReporterBlockMorph(false)
    }
    block.category = line.shape match {
      case BlockShape.Reporter => "operators"
      case _ => "control"
    }
    // Percent signs have syntactic meaning in Snap block specs. Doubling
    // preserves source-code percent signs as literal label text.
    block.setSpec(line.label.replace("%", "%%"))
    block.fixBlockColor(null, true)
    block.fixLayout()
    block.rerender()
    val image = block.fullImage()
    context.drawImage(image, x, y)
    image.height.toDouble
  }

  private def normalizeCanvasStyle(program: BeProgram, context: CanvasRenderingContext2D, canvas: Canvas, config: SnapCodeEditorConfig): Unit = {
    canvas.style.position = "relative"
    canvas.style.left = "0"
    canvas.style.top = "0"
    canvas.style.right = "auto"
    canvas.style.bottom = "auto"
    canvas.style.display = "block"
  }

  def destroy(): Unit = {
    destroyed = true
    if (frameHandle != 0) dom.window.cancelAnimationFrame(frameHandle)
  }

  override def renderInto(program: BeProgram, canvas: Canvas, config: SnapCodeEditorConfig): Unit = {
    if (!destroyed) {
      val context = createContext(canvas)
      normalizeCanvasStyle(program, context, canvas, config)
      val resized = resizeCanvasIfNeeded(program, context, canvas, config)
      if (dirty || resized) {
        drawProgram(program, context, canvas, config)
        dirty = false
      }
      frameHandle = dom.window.requestAnimationFrame(_ => drawProgram(program, context, canvas, config))
    }
  }

  private def expressionToLines(config: SnapCodeEditorConfig, expression: BeExpression, depth: Int): List[RenderLine] = expression match {
    case BeStartProgram(Some(sequence)) => RenderLine("when program starts", depth, BlockShape.Hat) :: expressionToLines(config, sequence, depth + 1)
    case BeStartProgram(None) => Nil
    case BeSequence(body, _) => body.flatMap(expressionToLines(config, _, depth))
    case other =>
      val hasSideEffects = other.staticInformationExpression.hasSideEffects
      val shape = if (hasSideEffects) BlockShape.Command else BlockShape.Reporter
      val ownLine = RenderLine(singleLineLabel(config, other), depth, shape)
      val childLines = other.getChildren(withExtensions = false, it.evadid.vm.types.BeScope.GlobalScope()).collect {
        case it.evadid.vm.code.tree.BeExpressionReference(_, childExpression) => childExpression
      }.flatMap(expressionToLines(config, _, depth + 1))
      ownLine :: childLines
  }

  private def singleLineLabel(config: SnapCodeEditorConfig, expression: BeExpression): String =
    expression.expressionIO.toStringWithConfig(config.DisplayConfig).replace('\n', ' ').replaceAll("\\s+", " ").trim match {
      case "" => expression.getClass.getSimpleName.stripSuffix("$")
      case rendered => rendered
    }

}