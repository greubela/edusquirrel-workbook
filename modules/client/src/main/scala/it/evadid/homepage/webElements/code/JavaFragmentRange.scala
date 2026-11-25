package it.evadid.homepage.webElements.code

import it.evadid.homepage.webElements.editor.code.codemirror.CodeMirrorEditor
import it.evadid.vm.parsing.source.SourceParsing
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.vm.naming.BeEntityName
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateJavaStructured

import scala.collection.immutable.VectorMap

/** Characters `[start, end)` of the source of class `className` in a structured Java answer. */
final case class JavaFragmentRange(className: BeEntityName, start: Int, end: Int) {
  private def within(source: String): Boolean = start >= 0 && start <= end && end <= source.length

  def text(state: ProgrammingStateJavaStructured): Option[String] =
    state.definedClasses.get(className).filter(within).map(_.substring(start, end))

  /** `state` with this range replaced by `draft`, and the range the draft then occupies. A class whose
    * edited declaration names another class that is not yet defined keeps its position under the new name.
    */
  def replace(state: ProgrammingStateJavaStructured, draft: String): Option[(ProgrammingStateJavaStructured, JavaFragmentRange)] =
    state.definedClasses.get(className).filter(within).map { source =>
      val edited = source.substring(0, start) + draft + source.substring(end)
      val key = JavaFragmentRange.declaredName(edited)
        .filterNot(name => state.definedClasses.keys.exists(JavaFragmentRange.sameName(_, name)))
        .getOrElse(className)
      val classes = VectorMap.from(state.definedClasses.map((name, value) => if name == className then key -> edited else name -> value))
      (ProgrammingStateJavaStructured(classes), JavaFragmentRange(key, start, start + draft.length))
    }

  /** Where the class of this fragment starts in the printed program (`state.toString`). */
  private def preceding(state: ProgrammingStateJavaStructured): Int =
    state.definedClasses.toSeq.takeWhile(_._1 != className).map(_._2.length + "\n\n".length).sum

  /** The line of the printed program (`state.toString`) on which this fragment starts. */
  def firstLineNumber(state: ProgrammingStateJavaStructured): Int =
    JavaMethodSource.firstLineNumber(state.toString, Some(JavaMethodSource.MethodRange(preceding(state) + start, preceding(state) + end)))

  /** A diagnostic of the printed program in this fragment's own lines and columns, if it lies within the fragment.
    * A diagnostic without columns marks whole lines.
    */
  def localize(state: ProgrammingStateJavaStructured, diagnostic: CodeMirrorEditor.Diagnostic): Option[CodeMirrorEditor.Diagnostic] = {
    val printed = state.toString
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

object JavaFragmentRange {
  /** The name of the first class declared in `source`. */
  def declaredName(source: String): Option[BeEntityName] =
    SourceParsing.parse(source, SourceLanguage.Java).structured.classes.headOption.map(owner => BeEntityName.fromCodeString(owner.name))

  def sameName(left: BeEntityName, right: BeEntityName): Boolean =
    left.universalInterpretation() == right.universalInterpretation()
}
