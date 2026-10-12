package it.evadid.homepage.webElements.editor.code.codemirror

import scala.collection.mutable
import scala.scalajs.js

private[code] final class CodeMirrorDiagnostics {
  import CodeMirrorApi.*
  import CodeMirrorDiagnostics.*

  val effect: EffectType[js.Array[js.Object]] = Libraries.StateEffect.define[js.Array[js.Object]]()

  val field: Field[DecorationSet] = Libraries.StateField.define[DecorationSet](js.Dynamic.literal(
    create = ((_: State) => Libraries.Decoration.none): js.Function1[State, DecorationSet],
    update = ((decorations: DecorationSet, transaction: Transaction) => {
      var next = decorations.map(transaction.changes)
      transaction.effects.foreach { current =>
        if current.is(effect) then next = build(transaction.state, current.value.asInstanceOf[js.Array[js.Object]])
      }
      next
    }): js.Function2[DecorationSet, Transaction, DecorationSet],
    provide = ((value: Field[DecorationSet]) => Libraries.EditorView.decorations.from(value)): js.Function1[Field[DecorationSet], Extension]
  ))

  def build(state: State, raw: js.Array[js.Object]): DecorationSet = {
    val byLine = mutable.LinkedHashMap.empty[Int, Vector[Normalized]]
    normalize(raw, state.doc.lines).foreach { diagnostic =>
      (diagnostic.line to diagnostic.endLine).foreach { line =>
        byLine.update(line, byLine.getOrElse(line, Vector.empty) :+ diagnostic)
      }
    }
    val ranges = js.Array[js.Object]()
    byLine.foreach { (number, items) =>
      val line = state.doc.line(number)
      plan(items, line.to - line.from).foreach { planned =>
        ranges.push(Libraries.Decoration.line(spec("cm-edusquirrel-diagnostic", planned.label)).range(line.from))
        planned.marks.foreach { mark =>
          ranges.push(Libraries.Decoration.mark(spec("cm-edusquirrel-diagnostic-mark", mark.label))
            .range(line.from + mark.from, line.from + mark.to))
        }
      }
    }
    Libraries.Decoration.set(ranges, true)
  }

  private def spec(base: String, label: Label): js.Object = {
    val attributes = js.Dictionary("data-diagnostic-severity" -> label.severity)
    if label.message.nonEmpty then attributes.update("title", label.message)
    val className = if label.severity == "warning" then base else s"$base $base-${label.severity}"
    js.Dynamic.literal(`class` = className, attributes = attributes)
  }
}

private[code] object CodeMirrorDiagnostics {
  final case class Normalized(
    line: Int,
    endLine: Int,
    fromCh: Option[Double],
    toCh: Option[Double],
    severity: String,
    message: String
  )
  final case class Label(severity: String, message: String)
  final case class Mark(from: Int, to: Int, label: Label)
  final case class LinePlan(label: Label, marks: Vector[Mark])

  def normalize(raw: js.Any, lines: Int): Vector[Normalized] = {
    if !js.Array.isArray(raw) || lines < 1 then Vector.empty
    else raw.asInstanceOf[js.Array[js.Any]].iterator.flatMap { item =>
      def property(name: String): js.Any =
        if item == null || js.isUndefined(item) then js.undefined
        else item.asInstanceOf[js.Dynamic].selectDynamic(name)
      def number(name: String): Double = js.Dynamic.global.Number(property(name)).asInstanceOf[Double]
      def text(name: String, fallback: String): String = {
        val value = property(name)
        js.Dynamic.global.String(if value == null || js.isUndefined(value) then fallback else value).asInstanceOf[String]
      }
      def column(name: String): Option[Double] = {
        val value = number(name)
        Option.when(value.isFinite)(math.max(0, math.floor(value)))
      }
      val line = number("line")
      if !line.isFinite || line != math.floor(line) || line < 1 || line > lines then None
      else {
        val end = number("endLine")
        val endLine = if end.isFinite then math.max(line, math.min(lines.toDouble, math.floor(end))).toInt else line.toInt
        val severity = text("severity", "warning").toLowerCase match {
          case "error" => "error"
          case "soft" | "info" => "soft"
          case _ => "warning"
        }
        Some(Normalized(line.toInt, endLine, column("fromCh"), column("toCh"), severity, text("message", "")))
      }
    }.toVector
  }

  def plan(items: Seq[Normalized], lineLength: Int): Option[LinePlan] = {
    def priority(severity: String): Int = severity match {
      case "error" => 2
      case "warning" => 1
      case _ => 0
    }
    val ordered = items.zipWithIndex.sortBy { (item, index) => (-priority(item.severity), index) }.map(_._1).toVector
    def label(active: Seq[Normalized]): Label =
      Label(active.head.severity, active.map(_.message).filter(_.nonEmpty).distinct.mkString("\n"))
    def bound(value: Double): Int = math.max(0, math.min(lineLength.toDouble, value)).toInt
    Option.when(ordered.nonEmpty) {
      val spans = ordered.flatMap { item =>
        for {
          from <- item.fromCh if item.line == item.endLine
          to <- item.toCh
          start = bound(from)
          end = bound(to) if end > start
        } yield (start, end, item)
      }
      val boundaries = spans.flatMap { (from, to, _) => Vector(from, to) }.distinct.sorted
      val marks = boundaries.sliding(2).flatMap {
        case Vector(from, to) =>
          val active = spans.collect { case (start, end, item) if start <= from && end >= to => item }
          Option.when(active.nonEmpty)(Mark(from, to, label(active)))
        case _ => None
      }.toVector
      LinePlan(label(ordered), marks)
    }
  }
}
