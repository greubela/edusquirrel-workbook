package it.evadid.homepage.webElements.editor.code.JavaEditor

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId

/** UI strings of [[JavaComponentEditor]], resolved from the `blockeditor` language map. */
final class JavaEditorLabels(text: String => Signal[String]) {
  import JavaEditorLabels.Key
  val warnings = it.evadid.homepage.webElements.editor.code.program.SourceWarningLabels(text)
  val uml = it.evadid.homepage.webElements.editor.code.EvaEditor.uml.UmlStructureSelector.OriginLabels(text)

  def apply(key: Key): Signal[String] = text(key.entry)

  /** Every label, so that a state-dependent text is one `map` rather than a switch between label signals. */
  lazy val all: Signal[Map[Key, String]] = Signal.combineSeq(Key.values.toSeq.map(key => apply(key).map(key -> _))).map(_.toMap)

  /** The label `key` selects for each value of `value`. */
  def select[A](value: Signal[A])(key: A => Key): Signal[String] = value.combineWith(all).map((current, texts) => texts(key(current)))
}

object JavaEditorLabels {
  /** Entries of the `blockeditor` language map; each name is its map key. */
  enum Key {
    case javaShowClasses, javaHideClasses, javaNavigation, classDiagram, javaTaskReference
    case javaDelete, javaAddAttribute, javaEditAttribute, javaAttributeName, javaAttributeType, javaInitializer, javaVisibility, javaAddConstructor, javaAssignFields, javaFieldExists
    case javaNoClasses, javaNoClassesHint, javaAddClass, javaAddFunction
    case javaModes, javaModeClass, javaModeFunction, javaModeFull
    case javaViewFunction, javaViewClass, javaViewSource, javaViewEmpty
    case javaSourceHeading, javaSelectionHeading, javaEditsKept, javaChooseEntry, javaChooseEntryHint, javaDraftWarning
    case javaClassName, javaFunctionName, javaReturnType, javaParameters, javaStaticMethod, cancel
    case javaInvalidClassName, javaClassExists, javaClassNotAdded
    case javaInvalidFunctionName, javaMissingReturnType, javaFunctionNotAdded

    def entry: String = toString
  }

  def fromContent(text: LanguageMapContentId => Signal[String]): JavaEditorLabels =
    JavaEditorLabels(key => text(LanguageMapContentId("blockeditor", key)))
}
