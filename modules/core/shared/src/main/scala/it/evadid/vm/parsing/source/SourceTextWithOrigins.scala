package it.evadid.vm.parsing.source

import upickle.default.ReadWriter

/** Text preparation keeps an original UTF-16 offset for each character, including after class hoisting. */
final case class SourceTextWithOrigins(text: String, offsets: Vector[Int]) derives ReadWriter {
  require(text.length == offsets.size)

  def slice(start: Int, end: Int): SourceTextWithOrigins =
    SourceTextWithOrigins(text.slice(start, end), offsets.slice(start, end))

  def trimmed: SourceTextWithOrigins = {
    val start = text.indexWhere(_ > ' ') match { case -1 => text.length; case index => index }
    slice(start, (text.lastIndexWhere(_ > ' ') + 1).max(start))
  }

  def without(ranges: Seq[(Int, Int)]): SourceTextWithOrigins = {
    val outer = ranges.sortBy(_._1).foldLeft(Vector.empty[(Int, Int)]) { (kept, next) =>
      if kept.lastOption.exists(_._2 >= next._2) then kept else kept :+ next
    }
    outer.reverse.foldLeft(this) { case (current, (start, end)) =>
      SourceTextWithOrigins(current.text.take(start) + current.text.drop(end), current.offsets.take(start) ++ current.offsets.drop(end))
    }
  }

  def dedented: SourceTextWithOrigins = {
    val indices = text.indices.filterNot(index => text(index) == '\n' && index > 0 && text(index - 1) == '\r').toVector
    val normalized = SourceTextWithOrigins(indices.map(i => if text(i) == '\r' then '\n' else text(i)).mkString, indices.map(offsets))
    val starts = Vector(0) ++ normalized.text.indices.filter(normalized.text(_) == '\n').map(_ + 1)
    val lines = starts.zip(starts.drop(1).map(_ - 1) :+ normalized.text.length).map(normalized.slice)
    val indent = lines.filter(_.text.trim.nonEmpty).map(_.text.takeWhile(c => c == ' ' || c == '\t').length).minOption.getOrElse(0)
    SourceTextWithOrigins.join(lines.map(line => line.slice(indent, line.text.length)), "\n").trimmed
  }

  def originalSpan(span: SourceSpan, original: String): Option[SourceSpan] = offsets.lift(span.start).orElse(Option.when(span.start == text.length)(offsets.lastOption.map(_ + 1)).flatten).map { start =>
    val end = if span.end > span.start then offsets.lift(span.end - 1).getOrElse(start) + 1 else start
    SourceLines(original).span(start.min(original.length), end.max(start).min(original.length))
  }
}

object SourceTextWithOrigins {
  def apply(text: String): SourceTextWithOrigins = SourceTextWithOrigins(text, text.indices.toVector)

  def join(parts: Seq[SourceTextWithOrigins], separator: String): SourceTextWithOrigins =
    parts.reduceOption { (left, right) =>
      val anchor = right.offsets.headOption.orElse(left.offsets.lastOption).getOrElse(0)
      SourceTextWithOrigins(left.text + separator + right.text, left.offsets ++ Vector.fill(separator.length)(anchor) ++ right.offsets)
    }.getOrElse(SourceTextWithOrigins("", Vector.empty))
}
