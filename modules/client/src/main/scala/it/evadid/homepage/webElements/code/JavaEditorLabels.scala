package it.evadid.homepage.webElements.code

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId

/** UI strings of the structure editor and its class diagram, from the `basic` language map.
  * UML notation (visibility markers, `name: Type`) is language neutral and not part of it.
  * With `python`, texts that name the language or Java's concepts come from the `python…` keys.
  */
final class JavaEditorLabels(text: String => Signal[String], python: Boolean = false) {
  private def specific(key: String): Signal[String] = text(if python then "python" + key else "java" + key)

  val title: Signal[String] = specific("EditorTitle")
  val subtitle: Signal[String] = specific("EditorSubtitle")
  val diagramView: Signal[String] = text("javaViewDiagram")
  val sourceView: Signal[String] = text("javaViewSource")
  val showDiagram: Signal[String] = text("javaShowDiagram")
  val hideDiagram: Signal[String] = text("javaHideDiagram")
  val diagramHint: Signal[String] = specific("DiagramHint")
  val diagramDescription: Signal[String] = specific("DiagramDescription")
  val draftNotice: Signal[String] = text("javaDraftNotice")
  val library: Signal[String] = text("javaLibrary")
  val uses: Signal[String] = text("javaUses")
  val inherits: Signal[String] = text("javaInherits")
  val realizes: Signal[String] = text("javaRealizes")
  val knows: Signal[String] = text("javaKnows")
  val relations: Signal[String] = text("javaRelations")
  val addClass: Signal[String] = text("javaAddClass")
  val addField: Signal[String] = text("javaAddAttribute")
  val editField: Signal[String] = text("javaEditAttribute")
  val addConstructor: Signal[String] = text("javaAddConstructor")
  val addMethod: Signal[String] = text("javaAddMethod")
  /** Python's module box and its functions. */
  val module: Signal[String] = text("pythonModule")
  val addFunction: Signal[String] = text("pythonAddFunction")
  val noClasses: Signal[String] = text("javaNoClasses")
  val noClassesHint: Signal[String] = text("javaNoClassesHint")
  val sourceTitle: Signal[String] = specific("SourceTitle")
  val sourceHint: Signal[String] = text("javaSourceHint")
  val chooseMethod: Signal[String] = specific("ChooseMethod")
  val chooseMethodHint: Signal[String] = specific("ChooseMethodHint")
  val methodMenu: Signal[String] = specific("MethodMenu")
  val draftWarning: Signal[String] = text("javaDraftWarning")
  val notJava: Signal[String] = specific("NotJava")
  val reference: Signal[String] = text("javaReference")
  val delete: Signal[String] = text("javaDelete")
  val deleteConfirm: Signal[String] = specific("DeleteConfirm")
  val save: Signal[String] = text("javaSave")
  val cancel: Signal[String] = text("javaCancel")
  val className: Signal[String] = text("javaClassName")
  val name: Signal[String] = text("javaName")
  val javaType: Signal[String] = specific("Type")
  val returnType: Signal[String] = specific("ReturnType")
  val initialValue: Signal[String] = text("javaInitialValue")
  val visibility: Signal[String] = text("javaVisibility")
  val isStatic: Signal[String] = specific("Static")
  val parameters: Signal[String] = text("javaParameters")
  val addParameter: Signal[String] = text("javaAddParameter")
  val removeParameter: Signal[String] = text("javaRemoveParameter")
  val assignFields: Signal[String] = specific("AssignAttributes")
  val preview: Signal[String] = specific("Preview")
  val errorClassName: Signal[String] = specific("ErrorClassName")
  val errorName: Signal[String] = specific("ErrorName")
  val errorType: Signal[String] = specific("ErrorType")
  val errorDuplicate: Signal[String] = text("javaErrorDuplicate")
  val errorInvalid: Signal[String] = specific("ErrorInvalid")
}

object JavaEditorLabels {
  def fromContent(content: LanguageMapContentId => Signal[String], python: Boolean = false): JavaEditorLabels =
    JavaEditorLabels(key => content(LanguageMapContentId(s"basic/$key")), python)

  /** The bare keys, for editors built without a language map (component tests). */
  val keys: JavaEditorLabels = JavaEditorLabels(key => Val(key))
}
