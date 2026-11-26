package it.evadid.homepage.webElements.editor.code.fragment

import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.vm.editing.JavaMethodEditing
import it.evadid.vm.naming.BeEntityName

/** Characters `[start, end)` of the source stored under `owner` in a structured answer ([[FragmentSource]]). */
final case class FragmentRange(owner: BeEntityName, start: Int, end: Int) {
  private def within(source: String): Boolean = start >= 0 && start <= end && end <= source.length

  def text[S](state: S)(using sources: FragmentSource[S]): Option[String] =
    sources.source(state, owner).filter(within).map(_.substring(start, end))

  /** `state` with this range replaced by `draft`, and the range the draft then occupies. */
  def replace[S](state: S, draft: String)(using sources: FragmentSource[S]): Option[(S, FragmentRange)] =
    sources.source(state, owner).filter(within).map { source =>
      val (next, key) = sources.updated(state, owner, source.substring(0, start) + draft + source.substring(end))
      (next, FragmentRange(key, start, start + draft.length))
    }

  /** Where the source of this fragment starts in the printed program. */
  private def preceding[S](state: S)(using sources: FragmentSource[S]): Int =
    sources.sources(state).takeWhile(_._1 != owner).map(_._2.length + "\n\n".length).sum

  /** The line of the printed program on which this fragment starts. */
  def firstLineNumber[S](state: S)(using sources: FragmentSource[S]): Int =
    JavaMethodEditing.firstLineNumber(sources.printed(state), Some(JavaMethodEditing.MethodRange(preceding(state) + start, preceding(state) + end)))

  /** A diagnostic of the printed program in this fragment's own lines and columns, if it lies within the fragment.
    * A diagnostic without columns marks whole lines.
    */
  def localize[S](state: S, diagnostic: CodeMirrorEditor.Diagnostic)(using sources: FragmentSource[S]): Option[CodeMirrorEditor.Diagnostic] = {
    val printed = sources.printed(state)
    val (first, last) = (preceding(state) + start, preceding(state) + end)
    val lineStarts = 0 +: printed.indices.filter(printed(_) == '\n').map(_ + 1)
    def lineEnd(line: Int) = lineStarts.lift(line).fold(printed.length)(_ - 1)
    // A whole-line mark keeps only the part of its line within the fragment.
    def offset(line: Int, column: Option[Int], atEnd: Boolean) =
      lineStarts.lift(line - 1).map(lineStart => column.fold(if atEnd then lineEnd(line).min(last) else lineStart.max(first))(lineStart + _))
    def local(position: Int): (Int, Int) = {
      val before = printed.substring(first, position)
      (before.count(_ == '\n') + 1, before.length - before.lastIndexOf('\n') - 1)
    }
    for {
      from <- offset(diagnostic.line, diagnostic.fromCh, atEnd = false)
      to <- offset(diagnostic.endLine.getOrElse(diagnostic.line), diagnostic.toCh, atEnd = true)
      if first <= from && to <= last
    } yield {
      val ((line, fromCh), (endLine, toCh)) = (local(from), local(to))
      diagnostic.copy(line = line, endLine = Some(endLine), fromCh = diagnostic.fromCh.map(_ => fromCh), toCh = diagnostic.toCh.map(_ => toCh))
    }
  }
}
