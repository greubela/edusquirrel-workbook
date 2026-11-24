package it.evadid.homepage.webElements.code

import it.evadid.vm.conversion.structured.SourceStructure
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

  /** The line of the printed program (`state.toString`) on which this fragment starts. */
  def firstLineNumber(state: ProgrammingStateJavaStructured): Int = {
    val preceding = state.definedClasses.toSeq.takeWhile(_._1 != className).map(_._2.length + "\n\n".length).sum
    JavaMethodSource.firstLineNumber(state.toString, Some(JavaMethodSource.MethodRange(preceding + start, preceding + end)))
  }
}

object JavaFragmentRange {
  /** The name of the first class declared in `source`. */
  def declaredName(source: String): Option[BeEntityName] =
    SourceStructure.javaClasses(source).headOption.map(block => BeEntityName.fromCodeString(block.name))

  def sameName(left: BeEntityName, right: BeEntityName): Boolean =
    left.universalInterpretation() == right.universalInterpretation()
}
