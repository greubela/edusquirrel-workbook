package it.evadid.homepage.webElements.editor.code.SnapRenderer

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.AppLanguage.{English, Python}
import it.evadid.homepage.webElements.HtmlAppElement
import it.evadid.vm.BeProgram
import it.evadid.vm.code.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.naming.CodeRepresentationConfig
import org.scalajs.dom
import org.scalajs.dom.CanvasRenderingContext2D

case class BeProgramSnapRenderer(program: Var[BeProgram]) extends HtmlAppElement {
  import BeProgramSnapRenderer.*

  private var mountedRenderer: Option[MountedProgramBlockRenderer] = None

  override def getDomElement(): L.Element = {
    div(
      cls := "be-program-snap-renderer",
      position.relative,
      overflow.hidden,
      border := "1px solid #d0d7de",
      borderRadius := "10px",
      backgroundColor := Colors.Workspace,
      minHeight := s"${CanvasHeight}px",
      width := "100%",
      canvasTag(
        cls := "be-program-snap-renderer__canvas",
        widthAttr := CanvasWidth,
        heightAttr := CanvasHeight,
        display.block,
        width := "100%",
        height := s"${CanvasHeight}px"
      ),
      onMountCallback { ctx =>
        val host = ctx.thisNode.ref
        val canvas = host.querySelector("canvas").asInstanceOf[dom.HTMLCanvasElement]
        val renderer = new MountedProgramBlockRenderer(host, canvas, program)
        mountedRenderer = Some(renderer)
        renderer.mount(ctx.owner)
      },
      onUnmountCallback { _ =>
        mountedRenderer.foreach(_.destroy())
        mountedRenderer = None
      }
    )
  }
}

object BeProgramSnapRenderer {
  private val CanvasWidth = 900
  private val CanvasHeight = 520
  private val Padding = 24.0
  private val BlockHeight = 32.0
  private val BlockGap = 8.0
  private val Indent = 28.0
  private val CornerRadius = 8.0
  private val Font = "14px sans-serif"
  private val MonospaceFont = "13px ui-monospace, SFMono-Regular, Menlo, Consolas, monospace"
  private val DisplayConfig = CodeRepresentationConfig(Python, English, skipUnparsable = false)

  private object Colors {
    val Workspace = "#f6f8fa"
    val Block = "#4f67c9"
    val Hat = "#2da44e"
    val Reporter = "#d29922"
    val Text = "#ffffff"
    val Shadow = "rgba(27,31,36,0.18)"
    val Empty = "#8c959f"
  }

  private final case class RenderLine(label: String, depth: Int, color: String, isReporter: Boolean = false)

  private final class MountedProgramBlockRenderer(host: dom.HTMLElement, canvas: dom.HTMLCanvasElement, program: Var[BeProgram]) {
    private val context = canvas.getContext("2d").asInstanceOf[CanvasRenderingContext2D]
    private var currentProgram = program.now()
    private var frameHandle: Int = 0
    private var destroyed = false
    private var dirty = true
    private var lastCssWidth = 0.0
    private var lastDpr = 0.0

    def mount(owner: com.raquo.airstream.ownership.Owner): Unit = {
      normalizeCanvasStyle()
      program.signal.foreach { nextProgram =>
        currentProgram = nextProgram
        dirty = true
      }(using owner)
      cycle()
    }

    private def cycle(): Unit = if (!destroyed) {
      normalizeCanvasStyle()
      val resized = resizeCanvasIfNeeded()
      if (dirty || resized) {
        drawProgram(currentProgram)
        dirty = false
      }
      frameHandle = dom.window.requestAnimationFrame(_ => cycle())
    }

    private def resizeCanvasIfNeeded(): Boolean = {
      val rect = host.getBoundingClientRect()
      val cssWidth = math.max(CanvasWidth.toDouble, rect.width)
      val dpr = math.max(1.0, dom.window.devicePixelRatio)
      val changed = cssWidth != lastCssWidth || dpr != lastDpr || canvas.width == 0 || canvas.height == 0
      if (changed) {
        canvas.width = (cssWidth * dpr).round.toInt
        canvas.height = (CanvasHeight * dpr).round.toInt
        canvas.style.width = "100%"
        canvas.style.height = s"${CanvasHeight}px"
        context.setTransform(dpr, 0, 0, dpr, 0, 0)
        lastCssWidth = cssWidth
        lastDpr = dpr
      }
      changed
    }

    private def drawProgram(value: BeProgram): Unit = {
      context.clearRect(0, 0, lastCssWidth, CanvasHeight)
      context.fillStyle = Colors.Workspace
      context.fillRect(0, 0, lastCssWidth, CanvasHeight)

      val lines = expressionToLines(value.fullProgram, 0)
      if (lines.isEmpty) drawEmptyProgram()
      else lines.zipWithIndex.foreach { case (line, index) => drawLine(line, Padding + index * (BlockHeight + BlockGap)) }
    }

    private def drawEmptyProgram(): Unit = {
      context.font = Font
      context.fillStyle = Colors.Empty
      context.fillText("No blocks yet", Padding, Padding + 20)
    }

    private def drawLine(line: RenderLine, y: Double): Unit = {
      val x = Padding + line.depth * Indent
      context.font = if (line.isReporter) MonospaceFont else Font
      val textWidth = context.measureText(line.label).width
      val width = math.max(140.0, textWidth + 28.0)
      drawRoundedRect(x + 2, y + 3, width, BlockHeight, CornerRadius, Colors.Shadow)
      drawRoundedRect(x, y, width, BlockHeight, if (line.isReporter) 16.0 else CornerRadius, line.color)
      context.fillStyle = Colors.Text
      context.fillText(line.label, x + 14, y + 21)
    }

    private def drawRoundedRect(x: Double, y: Double, width: Double, height: Double, radius: Double, color: String): Unit = {
      context.beginPath()
      context.moveTo(x + radius, y)
      context.lineTo(x + width - radius, y)
      context.quadraticCurveTo(x + width, y, x + width, y + radius)
      context.lineTo(x + width, y + height - radius)
      context.quadraticCurveTo(x + width, y + height, x + width - radius, y + height)
      context.lineTo(x + radius, y + height)
      context.quadraticCurveTo(x, y + height, x, y + height - radius)
      context.lineTo(x, y + radius)
      context.quadraticCurveTo(x, y, x + radius, y)
      context.closePath()
      context.fillStyle = color
      context.fill()
    }

    private def normalizeCanvasStyle(): Unit = {
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
  }

  private def expressionToLines(expression: BeExpression, depth: Int): List[RenderLine] = expression match {
    case BeStartProgram(Some(sequence)) => RenderLine("when program starts", depth, Colors.Hat) :: expressionToLines(sequence, depth + 1)
    case BeStartProgram(None) => Nil
    case BeSequence(body, _) => body.flatMap(expressionToLines(_, depth))
    case other =>
      val hasSideEffects = other.staticInformationExpression.hasSideEffects
      val ownLine = RenderLine(singleLineLabel(other), depth, if (hasSideEffects) Colors.Block else Colors.Reporter, !hasSideEffects)
      val childLines = other.getChildren(withExtensions = false, it.evadid.vm.types.BeScope.GlobalScope()).collect {
        case it.evadid.vm.code.tree.BeExpressionReference(_, childExpression) => childExpression
      }.flatMap(expressionToLines(_, depth + 1))
      ownLine :: childLines
  }

  private def singleLineLabel(expression: BeExpression): String =
    expression.expressionIO.toStringWithConfig(DisplayConfig).replace('\n', ' ').replaceAll("\\s+", " ").trim match {
      case "" => expression.getClass.getSimpleName.stripSuffix("$")
      case rendered => rendered
    }
}
