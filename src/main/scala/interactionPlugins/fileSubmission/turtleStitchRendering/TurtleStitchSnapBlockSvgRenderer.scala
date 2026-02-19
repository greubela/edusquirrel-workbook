package interactionPlugins.fileSubmission.turtleStitchRendering

import interactionPlugins.fileSubmission.turtleStitch.TurtleStitchProgramModel.*
import scala.scalajs.js.URIUtils

object TurtleStitchSnapBlockSvgRenderer {

  // Snap/TurtleStitch scale defaults from SyntaxElementMorph.setScale(1)
  private val corner = 3.0
  private val rounding = 9.0
  private val inset = 6.0
  private val dent = 8.0
  private val edge = 1.0
  private val hatHeight = 12.0
  private val hatWidth = 70.0
  private val fontSize = 10

  private final case class Theme(fill: String, bright: String, dark: String)
  private final case class Box(w: Double, h: Double, body: String)

  def renderScriptsAsSvgDataUrl(project: Project): Option[String] = {
    val scripts = selectScripts(project)
    if (scripts.isEmpty) None
    else {
      val rendered = scripts.map(renderScript)
      val width = rendered.map(_.w).foldLeft(0.0)(Math.max) + 20
      val height = rendered.map(_.h).sum + (rendered.size - 1).max(0) * 12 + 20
      val sb = new StringBuilder
      var y = 10.0
      rendered.zipWithIndex.foreach { case (b, i) =>
        sb.append(s"<g transform=\"translate(10,$y)\">${b.body}</g>")
        y += b.h + (if (i == rendered.size - 1) 0 else 12)
      }
      val svg = s"""<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"$width\" height=\"$height\" viewBox=\"0 0 $width $height\">
<rect width=\"$width\" height=\"$height\" fill=\"#fff\"/>
${sb.toString()}
</svg>"""
      Some(s"data:image/svg+xml;utf8,${URIUtils.encodeURIComponent(svg)}")
    }
  }

  private def selectScripts(project: Project): Vector[Script] = {
    val scene = project.scenes.lift(project.selectedScene - 1).orElse(project.scenes.headOption)
    scene.toVector.flatMap { s =>
      val sprite = s.stage.sprites.lift(s.stage.selectedSprite - 1).orElse(s.stage.sprites.headOption)
      sprite.map(_.scripts).getOrElse(s.stage.scripts)
    }
  }

  private def renderScript(script: Script): Box = {
    val blocks = script.blocks.map(renderBlock)
    val w = blocks.map(_.w).foldLeft(120.0)(Math.max)
    val sb = new StringBuilder
    var y = 0.0
    blocks.zipWithIndex.foreach { case (b, i) =>
      sb.append(s"<g transform=\"translate(0,$y)\">${b.body}</g>")
      y += b.h + (if (i == blocks.size - 1) 0 else 4)
    }
    Box(w, y.max(24), sb.toString())
  }

  private def renderBlock(block: BlockLike): Box = block match {
    case PrimitiveBlock(Some("receiveGo"), _, _, _) => renderHat("when green flag clicked")
    case PrimitiveBlock(Some(s), _, in, _) if s == "doRepeat" || s == "doRepeat:" => renderRepeat(in)
    case PrimitiveBlock(Some(s), _, in, _) if isReporter(s) => renderReporter(s, in)
    case PrimitiveBlock(Some(s), _, in, _) => renderCommand(s, in)
    case CustomBlockCall(spec, _, in, _, _) => renderCommand(spec, in)
    case _ => renderCommand("block", Vector.empty)
  }

  private def renderCommand(selector: String, inputs: Vector[InputValue]): Box = {
    val theme = themeFor(selector)
    val (label, literals) = buildLabel(selector, inputs)
    val width = (estimate(label) + literals.map(v => estimate(v) + 14).sum + 34).max(110)
    val height = 24.0
    val basePath = commandPath(width, height)
    val edgeOverlay = commandEdgeOverlay(width, height, theme)
    val txt = drawInlineLabelAndInputs(label, literals, theme)
    Box(width, height, s"<path d=\"$basePath\" fill=\"${theme.fill}\"/>$edgeOverlay$txt")
  }

  // adapted from CommandBlockMorph.outlinePath
  private def commandPath(width: Double, height: Double): String = {
    val indent = corner * 2 + inset
    val bottom = height - corner
    val bottomCorner = height - corner * 2
    s"M0 $corner Q0 0 $corner 0 L${corner + inset} 0 L$indent $corner L${indent + dent} $corner L${corner * 3 + inset + dent} 0 L${width - corner} 0 Q$width 0 $width $corner L$width $bottomCorner Q$width $height ${width - corner} $height L${corner * 3 + inset + dent} ${bottom} L${indent + dent} ${bottom + corner} L$indent ${bottom + corner} L${corner + inset} $bottom L$corner $height Q0 $height 0 $bottomCorner Z"
  }

  // mimics non-flat 3D effect from drawTopDentEdge/drawBottomDentEdge/left-right edges
  private def commandEdgeOverlay(width: Double, height: Double, theme: Theme): String = {
    val top = s"<path d=\"M$corner 0 L${width - corner} 0\" stroke=\"${theme.bright}\" stroke-width=\"$edge\" fill=\"none\"/>"
    val left = s"<path d=\"M0 $corner L0 ${height - corner}\" stroke=\"${theme.bright}\" stroke-width=\"$edge\" fill=\"none\"/>"
    val bottom = s"<path d=\"M$corner $height L${width - corner} $height\" stroke=\"${theme.dark}\" stroke-width=\"$edge\" fill=\"none\"/>"
    val right = s"<path d=\"M$width $corner L$width ${height - corner}\" stroke=\"${theme.dark}\" stroke-width=\"$edge\" fill=\"none\"/>"
    top + left + bottom + right
  }

  private def renderHat(text: String): Box = {
    val theme = Theme("#D98C00", "#F0B541", "#A56A00")
    val width = (estimate(text) + 36).max(hatWidth * 1.5)
    val height = 36.0
    val s = hatWidth
    val h = hatHeight
    val r = ((4 * h * h) + (s * s)) / (8 * h)
    val a = Math.toDegrees(4 * Math.atan(2 * h / s))
    val sa = a / 2
    val sp = Math.min(s * 1.7, width - corner)
    val path = s"M0 ${h + corner} A$r $r 0 0 1 ${s / 2 + r * Math.cos(Math.toRadians(-90))} ${r + r * Math.sin(Math.toRadians(-90))} C$s 0 $s $h $sp $h L${width - corner} $h Q$width $h $width ${h + corner} L$width ${height - corner * 2} Q$width $height ${width - corner} $height L${corner * 3 + inset + dent} ${height - corner} L${corner * 2 + inset + dent} $height L${corner * 2 + inset} $height L${corner + inset} ${height - corner} L$corner $height Q0 $height 0 ${height - corner * 2} Z"
    val body = s"<path d=\"$path\" fill=\"${theme.fill}\"/><path d=\"M4 ${h + 1} L${width - 4} ${h + 1}\" stroke=\"${theme.bright}\" stroke-width=\"1\"/><text x=\"12\" y=\"22\" font-family=\"Verdana,sans-serif\" font-size=\"11\" fill=\"#fff\">${esc(text)}</text>"
    Box(width, height, body)
  }

  private def renderRepeat(inputs: Vector[InputValue]): Box = {
    val times = inputs.collectFirst { case Literal(v) => v }.getOrElse("10")
    val nested = inputs.collectFirst { case NestedScript(s) => renderScript(s) }
    val head = renderCommand("doRepeat", Vector(Literal(times)))
    val slotH = nested.map(_.h + 8).getOrElse(34.0)
    val width = head.w.max(nested.map(_.w + 38).getOrElse(160.0))
    val totalH = head.h + slotH + 10
    val notchX = corner * 2 + inset
    val path = s"M0 0 H$width V${head.h} H${notchX + dent + 6} V${head.h + slotH} H$width V$totalH H0 V${head.h + slotH} H${notchX - 1} V${head.h} H0 Z"
    val theme = themeFor("doRepeat")
    val nestedBody = nested.map(n => s"<g transform=\"translate(${notchX + 10},${head.h + 5})\">${n.body}</g>").getOrElse("")
    val body = s"<path d=\"$path\" fill=\"${theme.fill}\"/>${commandEdgeOverlay(width,totalH,theme)}${head.body}<rect x=\"${notchX + 5}\" y=\"${head.h + 2}\" width=\"${width - notchX - 10}\" height=\"${slotH - 4}\" fill=\"rgba(255,255,255,0.18)\"/>$nestedBody"
    Box(width, totalH, body)
  }

  private def renderReporter(selector: String, inputs: Vector[InputValue]): Box = {
    val theme = Theme("#5E677D", "#8A92A5", "#3D4454")
    val label = (selectorToText(selector).replace("%s", "") + " " + inputs.collect { case Literal(v) => v }.mkString(" ")).trim
    val width = (estimate(label) + 24).max(70)
    val height = 22.0
    val path =
      if (selector == "=" || selector == "<" || selector == ">") {
        val h2 = Math.floor(height / 2)
        val right = width - rounding
        s"M0 $h2 L$rounding 0 L$right 0 L$width $h2 L$right $height L$rounding $height Z"
      } else {
        val r = Math.min(rounding, height / 2)
        s"M$r 0 H${width - r} A$r $r 0 0 1 $width $r V${height - r} A$r $r 0 0 1 ${width - r} $height H$r A$r $r 0 0 1 0 ${height - r} V$r A$r $r 0 0 1 $r 0 Z"
      }
    val body = s"<path d=\"$path\" fill=\"${theme.fill}\"/><path d=\"M2 1 L${width - 2} 1\" stroke=\"${theme.bright}\" stroke-width=\"1\"/><path d=\"M2 ${height - 1} L${width - 2} ${height - 1}\" stroke=\"${theme.dark}\" stroke-width=\"1\"/><text x=\"10\" y=\"15\" font-family=\"Verdana,sans-serif\" font-size=\"10\" fill=\"#fff\">${esc(label)}</text>"
    Box(width, height, body)
  }

  private def drawInlineLabelAndInputs(label: String, literals: Vector[String], theme: Theme): String = {
    val parts = label.split("%s", -1)
    val sb = new StringBuilder
    var x = 8.0
    parts.zipWithIndex.foreach { case (p, i) =>
      val t = p.trim
      if (t.nonEmpty) {
        sb.append(s"<text x=\"$x\" y=\"16\" font-family=\"Verdana,sans-serif\" font-size=\"$fontSize\" fill=\"#fff\">${esc(t)}</text>")
        x += estimate(t) + 6
      }
      if (i < literals.size) {
        val v = literals(i)
        val w = (estimate(v) + 12).max(20)
        sb.append(s"<rect x=\"$x\" y=\"3\" width=\"$w\" height=\"18\" rx=\"9\" ry=\"9\" fill=\"#fff\" stroke=\"rgba(0,0,0,0.25)\"/>")
        sb.append(s"<text x=\"${x + 6}\" y=\"16\" font-family=\"Verdana,sans-serif\" font-size=\"10\" fill=\"#333\">${esc(v)}</text>")
        x += w + 6
      }
    }
    sb.toString()
  }

  private def buildLabel(selector: String, inputs: Vector[InputValue]): (String, Vector[String]) =
    (selectorToText(selector), inputs.collect { case Literal(v) => v })

  private def selectorToText(selector: String): String = selector match {
    case "forward" | "forward:" => "move %s steps"
    case "turn" | "turn:" | "turnRight" | "turnRight:" => "turn ↻ %s degrees"
    case "turnLeft" | "turnLeft:" => "turn ↺ %s degrees"
    case "arcRight" => "arc right radius %s degrees %s"
    case "arcLeft" => "arc left radius %s degrees %s"
    case "gotoXY" | "gotoX:y:" => "go to x: %s y: %s"
    case "setHeading" | "heading:" => "point in direction %s"
    case "changeYPosition" | "changeYposBy:" => "change y by %s"
    case "clear" | "clearPenTrails" => "clear"
    case "up" | "penup" => "pen up"
    case "down" | "pendown" => "pen down"
    case "doRepeat" | "doRepeat:" => "repeat %s"
    case other => other
  }

  private def isReporter(s: String): Boolean = Set("xPosition", "yPosition", "heading", "=", "<", ">").contains(s)

  private def themeFor(selector: String): Theme = selector match {
    case "doRepeat" | "doRepeat:" | "receiveGo" => Theme("#FFAB19", "#FFD17A", "#CC8400")
    case "up" | "penup" | "down" | "pendown" | "clear" | "clearPenTrails" => Theme("#0FBD8C", "#43D8AB", "#0A8B67")
    case _ => Theme("#4C97FF", "#80B9FF", "#2E72CC")
  }

  private def estimate(s: String): Double = s.length * 6.2
  private def esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
