package it.evadid.homepage.webElements.code

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId

/** UI strings of [[JavaComponentEditor]], resolved from the `blockeditor` language map. */
final class JavaEditorLabels(text: String => Signal[String]) {
  import JavaEditorLabels.Key

  def apply(key: Key): Signal[String] = text(key.entry)

  /** Every label, so that a state-dependent text is one `map` rather than a switch between label signals. */
  lazy val all: Signal[Map[Key, String]] = Signal.combineSeq(Key.values.toSeq.map(key => apply(key).map(key -> _))).map(_.toMap)

  /** The label `key` selects for each value of `value`. */
  def select[A](value: Signal[A])(key: A => Key): Signal[String] = value.combineWith(all).map((current, texts) => texts(key(current)))
}

object JavaEditorLabels {
  /** Entries of the `blockeditor` language map; each name is its map key. */
  enum Key {
    case javaTitle, javaSubtitle, javaShowClasses, javaHideClasses, javaNavigation, javaTaskReference
    case javaNoClasses, javaNoClassesHint, javaAddClass, javaAddFunction
    case javaLibraryClass, javaLibraryClassHint, javaUnknownClass, javaUnknownClassHint, javaUnknownUse, javaUnreadable
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
