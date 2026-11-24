package it.evadid.homepage.webElements.code

import com.raquo.laminar.api.L.*
import it.evadid.core.datastructures.language.LanguageMapContentId

/** UI strings of the Java editor and its class diagram, from the `basic` language map.
  * UML notation (visibility markers, `name: Type`) is language neutral and not part of it.
  */
final class JavaEditorLabels(text: String => Signal[String]) {
  val title: Signal[String] = text("javaEditorTitle")
  val subtitle: Signal[String] = text("javaEditorSubtitle")
  val diagramView: Signal[String] = text("javaViewDiagram")
  val sourceView: Signal[String] = text("javaViewSource")
  val showDiagram: Signal[String] = text("javaShowDiagram")
  val hideDiagram: Signal[String] = text("javaHideDiagram")
  val diagramHint: Signal[String] = text("javaDiagramHint")
  val diagramDescription: Signal[String] = text("javaDiagramDescription")
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
  val noClasses: Signal[String] = text("javaNoClasses")
  val noClassesHint: Signal[String] = text("javaNoClassesHint")
  val sourceTitle: Signal[String] = text("javaSourceTitle")
  val sourceHint: Signal[String] = text("javaSourceHint")
  val chooseMethod: Signal[String] = text("javaChooseMethod")
  val chooseMethodHint: Signal[String] = text("javaChooseMethodHint")
  val methodMenu: Signal[String] = text("javaMethodMenu")
  val draftWarning: Signal[String] = text("javaDraftWarning")
  val notJava: Signal[String] = text("javaNotJava")
  val reference: Signal[String] = text("javaReference")
  val delete: Signal[String] = text("javaDelete")
  val deleteConfirm: Signal[String] = text("javaDeleteConfirm")
  val save: Signal[String] = text("javaSave")
  val cancel: Signal[String] = text("javaCancel")
  val className: Signal[String] = text("javaClassName")
  val name: Signal[String] = text("javaName")
  val javaType: Signal[String] = text("javaType")
  val returnType: Signal[String] = text("javaReturnType")
  val initialValue: Signal[String] = text("javaInitialValue")
  val visibility: Signal[String] = text("javaVisibility")
  val isStatic: Signal[String] = text("javaStatic")
  val parameters: Signal[String] = text("javaParameters")
  val addParameter: Signal[String] = text("javaAddParameter")
  val removeParameter: Signal[String] = text("javaRemoveParameter")
  val assignFields: Signal[String] = text("javaAssignAttributes")
  val preview: Signal[String] = text("javaPreview")
  val errorClassName: Signal[String] = text("javaErrorClassName")
  val errorName: Signal[String] = text("javaErrorName")
  val errorType: Signal[String] = text("javaErrorType")
  val errorDuplicate: Signal[String] = text("javaErrorDuplicate")
  val errorInvalid: Signal[String] = text("javaErrorInvalid")
}

object JavaEditorLabels {
  def fromContent(content: LanguageMapContentId => Signal[String]): JavaEditorLabels =
    JavaEditorLabels(key => content(LanguageMapContentId(s"basic/$key")))

  /** The bare keys, for editors built without a language map (component tests). */
  val keys: JavaEditorLabels = JavaEditorLabels(key => Val(key))
}
