package it.evadid.homepage.webElements.editor.code.fragment

import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python}
import it.evadid.vm.editing.PythonMethodEditing
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.parsing.source.{SourceParsing, SourceSpan}
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.workbook.elements.interactionElements.programming.state.{ProgrammingStateJavaStructured, ProgrammingStatePythonStructured}

import scala.collection.immutable.VectorMap

/** A structured answer `S` as the named sources a [[FragmentCodeEditor]] edits: Java's classes, Python's classes
  * and module. The printed program joins `sources` with a blank line.
  */
trait FragmentSource[S] {
  def language: ProgrammingLanguage

  /** The sources in the order the printed program shows them. */
  def sources(state: S): Seq[(BeEntityName, String)]

  def source(state: S, owner: BeEntityName): Option[String]

  /** `state` with `owner`'s source replaced by `code`, and the owner's key afterwards (a renamed class). */
  def updated(state: S, owner: BeEntityName, code: String): (S, BeEntityName)

  def printed(state: S): String

  /** Whether `[start, end)` of one owner's source `code` holds only whitespace and comments besides `spans`. */
  def onlyTriviaAround(code: String, start: Int, end: Int, spans: Seq[SourceSpan]): Boolean
}

object FragmentSource {
  given java: FragmentSource[ProgrammingStateJavaStructured] with {
    def language: ProgrammingLanguage = Java
    def sources(state: ProgrammingStateJavaStructured): Seq[(BeEntityName, String)] = state.definedClasses.toSeq
    def source(state: ProgrammingStateJavaStructured, owner: BeEntityName): Option[String] = state.definedClasses.get(owner)
    def printed(state: ProgrammingStateJavaStructured): String = state.toString

    /** A class whose edited declaration names another class that is not yet defined keeps its position under the new name. */
    def updated(state: ProgrammingStateJavaStructured, owner: BeEntityName, code: String): (ProgrammingStateJavaStructured, BeEntityName) = {
      val key = declaredJavaClass(code).filterNot(name => state.definedClasses.keys.exists(sameName(_, name))).getOrElse(owner)
      (ProgrammingStateJavaStructured(VectorMap.from(state.definedClasses.map((name, value) => if name == owner then key -> code else name -> value))), key)
    }
    def onlyTriviaAround(code: String, start: Int, end: Int, spans: Seq[SourceSpan]): Boolean =
      SourceParsing.parse(code, SourceLanguage.Java).structured.onlyTriviaAround(start, end, spans)
  }

  given python: FragmentSource[ProgrammingStatePythonStructured] with {
    def language: ProgrammingLanguage = Python
    def sources(state: ProgrammingStatePythonStructured): Seq[(BeEntityName, String)] =
      (state.definedClasses.toSeq :+ (PythonMethodEditing.module -> state.mainScript)).filter(_._2.nonEmpty)
    def source(state: ProgrammingStatePythonStructured, owner: BeEntityName): Option[String] = PythonMethodEditing.source(state, owner)
    def printed(state: ProgrammingStatePythonStructured): String = state.toString
    def updated(state: ProgrammingStatePythonStructured, owner: BeEntityName, code: String): (ProgrammingStatePythonStructured, BeEntityName) =
      (PythonMethodEditing.updated(state, owner, code), PythonMethodEditing.ownerAfter(owner, code))
    /** Python reads code by logical lines: a code line starting in the range outside `spans` is code around them. */
    def onlyTriviaAround(code: String, start: Int, end: Int, spans: Seq[SourceSpan]): Boolean =
      !SourceParsing.parse(code, SourceLanguage.Python).structured.codeLines.exists(line =>
        start <= line.span.start && line.span.start < end && !spans.exists(_.contains(line.span.start)))
  }

  /** The name of the first Java class declared in `source`. */
  def declaredJavaClass(source: String): Option[BeEntityName] =
    SourceParsing.parse(source, SourceLanguage.Java).structured.classes.headOption.map(owner => BeEntityName.fromCodeString(owner.name))

  def sameName(left: BeEntityName, right: BeEntityName): Boolean =
    left.universalInterpretation() == right.universalInterpretation()
}
