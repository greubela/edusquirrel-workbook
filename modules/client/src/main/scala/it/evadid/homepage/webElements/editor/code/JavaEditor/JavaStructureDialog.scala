package it.evadid.homepage.webElements.editor.code.JavaEditor

import it.evadid.vm.editing.JavaMethodEditing
import com.raquo.laminar.api.L.{label as labelTag, *}
import it.evadid.homepage.webElements.basic.PageScrollLock
import it.evadid.homepage.webElements.editor.code.JavaEditor.JavaEditorLabels.Key
import it.evadid.vm.naming.BeEntityName
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStateJavaStructured
import org.scalajs.dom

import scala.collection.immutable.VectorMap

/** The modal dialogs that add a class or a function to a structured Java answer.
  *
  * A successful request writes the extended answer to `state` and passes the new entry to `reveal`: its class and,
  * for a function, the function's source.
  */
final class JavaStructureDialog(
    state: Var[ProgrammingStateJavaStructured],
    labels: JavaEditorLabels,
    reveal: (BeEntityName, Option[String]) => Unit
) {
  import JavaMethodEditing.*
  import JavaStructureDialog.*

  private val request = Var(Option.empty[Request])
  private val error = Var(Option.empty[Key])

  def addClass(): Unit = open(Request.AddClass(Var("")))

  def addFunction(className: BeEntityName): Unit = open(Request.AddFunction(className, Var(MethodFields())))

  def addField(className: BeEntityName): Unit = open(Request.Field(className, None, Var(FieldFields())))

  def editField(className: BeEntityName, field: it.evadid.vm.parsing.source.JavaFieldStructured): Unit =
    open(Request.Field(className, Some(field), Var(FieldFields(field.name, field.javaType,
      field.initializer.getOrElse(""), field.modifiers.find(Set("public", "private", "protected")).getOrElse(""), field.isStatic))))

  def addConstructor(className: BeEntityName): Unit = open(Request.Constructor(className, Var(""), Var(true)))

  private def sourceOf(className: BeEntityName) = state.now().definedClasses.get(className)
  private def ownerOf(text: String) = discover(text).headOption

  private def commitMember(className: BeEntityName, edited: String, function: Boolean, before: String): Unit = {
    val parsed = it.evadid.vm.parsing.source.SourceParsing.parse(edited,
      it.evadid.vm.parsing.source.SourceParsing.SourceLanguage.Java)
    if parsed.structured.balanced && parsed.syntax.isRight && ownerOf(edited).nonEmpty then {
      state.update(current => ProgrammingStateJavaStructured(current.definedClasses.updated(className, edited)))
      close()
      val added = if function then ownerOf(edited).toSeq.flatMap(_.functions).find(method => !before.contains(method.code)).map(_.code) else None
      reveal(className, added)
    } else error.set(Some(Key.javaFunctionNotAdded))
  }

  private def saveField(className: BeEntityName, previous: Option[it.evadid.vm.parsing.source.JavaFieldStructured], fields: FieldFields): Unit =
    for text <- sourceOf(className); owner <- ownerOf(text) do {
      val currentField = previous.flatMap(old => owner.fields.find(field => field.name == old.name && field.code == old.code))
      if previous.nonEmpty && (currentField.isEmpty || owner.fields.count(_.span == currentField.get.span) != 1) then error.set(Some(Key.javaFunctionNotAdded))
      else if !validName(fields.name.trim) || fields.javaType.trim.isEmpty then error.set(Some(Key.javaInvalidFunctionName))
      else if owner.fields.exists(field => field.name == fields.name.trim && !previous.exists(_.name == field.name)) then
        error.set(Some(Key.javaFieldExists))
      else {
        val declaration = s"${fields.visibility} ${if fields.isStatic then "static " else ""}${fields.javaType.trim} ${fields.name.trim}" +
          Option.when(fields.initializer.trim.nonEmpty)(s" = ${fields.initializer.trim}").getOrElse("") + ";"
        val edited = currentField.fold(insertMethod(text, owner, declaration))(field =>
          text.substring(0, field.span.start) + declaration + text.substring(field.span.end))
        commitMember(className, edited, false, text)
      }
    }

  private def submitConstructor(className: BeEntityName, parameters: String, assign: Boolean): Unit =
    for text <- sourceOf(className); owner <- ownerOf(text) do {
      val declaration = s"public ${owner.name}($parameters) {\n  }"
      val candidate = insertMethod(text, owner, declaration)
      val constructor = ownerOf(candidate).toSeq.flatMap(_.functions).find(method => method.isConstructor && method.span.start >= closingBrace(owner))
      constructor match {
        case Some(method) if !owner.functions.exists(existing => existing.isConstructor && existing.parameters.map(_.javaType) == method.parameters.map(_.javaType)) =>
          val assignments = if assign then method.parameters.filter(parameter => owner.fields.exists(field => field.name == parameter.name && !field.isStatic))
            .map(parameter => s"    this.${parameter.name} = ${parameter.name};").mkString("\n") else ""
          val complete = s"public ${owner.name}($parameters) {\n$assignments\n  }"
          commitMember(className, insertMethod(text, owner, complete), true, text)
        case _ => error.set(Some(Key.javaFunctionNotAdded))
      }
    }

  /** The open dialog, if any. */
  val node: Modifier[HtmlElement] = child <-- request.signal.map(_.fold(emptyNode)(render))

  private def open(next: Request): Unit = {
    error.set(None)
    request.set(Some(next))
  }

  private def close(): Unit = request.set(None)

  private def submitClass(name: String): Unit = {
    val current = state.now()
    val key = BeEntityName.fromCodeString(name)
    if !validName(name) then error.set(Some(Key.javaInvalidClassName))
    else if current.definedClasses.keys.exists(JavaFragmentRange.sameName(_, key)) then error.set(Some(Key.javaClassExists))
    else {
      val next = ProgrammingStateJavaStructured(VectorMap.from(current.definedClasses) + (key -> s"class $name {\n}"))
      if parses(next.toString) then {
        state.set(next)
        close()
        reveal(key, None)
      } else error.set(Some(Key.javaClassNotAdded))
    }
  }

  private def submitFunction(className: BeEntityName, fields: MethodFields): Unit = {
    val current = state.now()
    val name = fields.name.trim
    if !validName(name) then error.set(Some(Key.javaInvalidFunctionName))
    else if fields.returnType.trim.isEmpty then error.set(Some(Key.javaMissingReturnType))
    else for {
      text <- current.definedClasses.get(className)
      target <- discover(text).headOption
    } {
      val edited = insertMethod(text, target, methodDeclaration(fields.returnType.trim, name, fields.parameters.trim, fields.isStatic))
      val next = ProgrammingStateJavaStructured(current.definedClasses.updated(className, edited))
      if parses(next.toString) then {
        state.set(next)
        close()
        reveal(className, discover(edited).flatMap(_.functions)
          .find(method => method.span.start >= closingBrace(target) && method.name == name)
          .map(_.code))
      } else error.set(Some(Key.javaFunctionNotAdded))
    }
  }

  private def render(current: Request): Element = {
    val (caption, fields, submit) = current match {
      case Request.AddClass(name) =>
        (Key.javaAddClass, field(Key.javaClassName, name), () => submitClass(name.now().trim))
      case Request.Field(className, previous, draft) => (if previous.isEmpty then Key.javaAddAttribute else Key.javaEditAttribute,
        div(cls := "java-function-editor__dialog-fields",
          field(Key.javaAttributeName, draft.zoomLazy(_.name)((v, n) => v.copy(name = n))),
          field(Key.javaAttributeType, draft.zoomLazy(_.javaType)((v, n) => v.copy(javaType = n))),
          field(Key.javaInitializer, draft.zoomLazy(_.initializer)((v, n) => v.copy(initializer = n))),
          labelTag(cls := "java-function-editor__field", span(child.text <-- labels(Key.javaVisibility)),
            select(value <-- draft.signal.map(_.visibility), onChange.mapToValue --> (next => draft.update(_.copy(visibility = next))),
              Seq("public", "protected", "private", "").map(v => option(value := v, if v.isEmpty then "~" else v)))),
          labelTag(input(typ := "checkbox", checked <-- draft.signal.map(_.isStatic),
            onChange.mapToChecked --> (next => draft.update(_.copy(isStatic = next)))), span(child.text <-- labels(Key.javaStaticMethod)))),
        () => saveField(className, previous, draft.now()))
      case Request.Constructor(className, parameters, assign) => (Key.javaAddConstructor,
        div(cls := "java-function-editor__dialog-fields", field(Key.javaParameters, parameters),
          labelTag(input(typ := "checkbox", checked <-- assign.signal, onChange.mapToChecked --> assign.writer),
            span(child.text <-- labels(Key.javaAssignFields)))), () => submitConstructor(className, parameters.now(), assign.now()))
      case Request.AddFunction(className, method) => (Key.javaAddFunction, div(cls := "java-function-editor__dialog-fields",
        field(Key.javaFunctionName, method.zoomLazy(_.name)((value, next) => value.copy(name = next))),
        field(Key.javaReturnType, method.zoomLazy(_.returnType)((value, next) => value.copy(returnType = next))),
        field(Key.javaParameters, method.zoomLazy(_.parameters)((value, next) => value.copy(parameters = next))),
        labelTag(cls := "java-function-editor__method-option",
          input(typ := "checkbox", checked <-- method.signal.map(_.isStatic),
            onChange.mapToChecked --> (checked => method.update(_.copy(isStatic = checked)))),
          span(child.text <-- labels(Key.javaStaticMethod)))), () => submitFunction(className, method.now()))
    }
    val deleteButton: Option[Element] = current match {
          case Request.Field(className, Some(old), _) => Some(button(typ := "button", cls := "java-function-editor__button",
            child.text <-- labels(Key.javaDelete), onClick --> { _ =>
              for text <- sourceOf(className); owner <- ownerOf(text); field <- owner.fields.find(field => field.name == old.name && field.code == old.code)
                if owner.fields.count(_.span == field.span) == 1 do
                commitMember(className, text.substring(0, field.span.start) + text.substring(field.span.end), false, text)
            }))
          case _ => None
    }
    dialogTag(role := "dialog", aria.label <-- labels(caption), cls := "java-function-editor__dialog",
      new EventProp[dom.Event]("cancel") --> { event =>
        event.preventDefault()
        event.stopPropagation()
        close()
      },
      onMountCallback(ctx => ctx.thisNode.ref.asInstanceOf[dom.html.Dialog].showModal()),
      onUnmountCallback(node => node.ref.asInstanceOf[dom.html.Dialog].close()),
      PageScrollLock.whileMounted,
      h2(cls := "java-function-editor__dialog-title", child.text <-- labels(caption)),
      fields,
      p(cls := "java-function-editor__dialog-error", role := "alert",
        child.text <-- error.signal.combineWith(labels.all).map((key, texts) => key.fold("")(texts))),
      div(cls := "java-function-editor__dialog-actions",
        deleteButton,
        button(typ := "button", cls := "java-function-editor__button java-function-editor__button--secondary",
          child.text <-- labels(Key.cancel), onClick --> (_ => close())),
        button(typ := "button", cls := "java-function-editor__button java-function-editor__button--primary",
          child.text <-- labels(caption), onClick --> (_ => submit()))))
  }

  private def field(caption: Key, fieldValue: Var[String]): Element =
    labelTag(cls := "java-function-editor__field", span(child.text <-- labels(caption)),
      input(typ := "text", value <-- fieldValue.signal, onInput.mapToValue --> fieldValue.writer))
}

object JavaStructureDialog {
  private final case class MethodFields(name: String = "newFunction", returnType: String = "void",
      parameters: String = "", isStatic: Boolean = true)

  private final case class FieldFields(name: String = "value", javaType: String = "int", initializer: String = "", visibility: String = "private", isStatic: Boolean = false) derives upickle.default.ReadWriter

  private enum Request {
    case Field(className: BeEntityName, previous: Option[it.evadid.vm.parsing.source.JavaFieldStructured], fields: Var[FieldFields])
    case Constructor(className: BeEntityName, parameters: Var[String], assign: Var[Boolean])
    case AddClass(name: Var[String])
    case AddFunction(className: BeEntityName, fields: Var[MethodFields])
  }

  private def validName(name: String): Boolean = name.matches("[A-Za-z_$][A-Za-z0-9_$]*")
}
