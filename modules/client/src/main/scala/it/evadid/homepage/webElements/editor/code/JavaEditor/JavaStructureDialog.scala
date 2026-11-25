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
      case Request.AddFunction(className, method) => (Key.javaAddFunction, div(cls := "java-function-editor__dialog-fields",
        field(Key.javaFunctionName, method.zoomLazy(_.name)((value, next) => value.copy(name = next))),
        field(Key.javaReturnType, method.zoomLazy(_.returnType)((value, next) => value.copy(returnType = next))),
        field(Key.javaParameters, method.zoomLazy(_.parameters)((value, next) => value.copy(parameters = next))),
        labelTag(cls := "java-function-editor__method-option",
          input(typ := "checkbox", checked <-- method.signal.map(_.isStatic),
            onChange.mapToChecked --> (checked => method.update(_.copy(isStatic = checked)))),
          span(child.text <-- labels(Key.javaStaticMethod)))), () => submitFunction(className, method.now()))
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

  private enum Request {
    case AddClass(name: Var[String])
    case AddFunction(className: BeEntityName, fields: Var[MethodFields])
  }

  private def validName(name: String): Boolean = name.matches("[A-Za-z_$][A-Za-z0-9_$]*")
}
