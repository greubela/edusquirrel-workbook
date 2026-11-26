package it.evadid.vm.structure

import it.evadid.core.datastructures.uml.*
import it.evadid.vm.editing.PythonMethodEditing
import it.evadid.vm.parsing.source.{SourceParsing, PythonFunctionStructured}
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.vm.parsing.python.clean.model.PyAST.*
import it.evadid.vm.parsing.python.clean.model.PyAST
import it.evadid.vm.naming.BeEntityName
import it.evadid.core.datastructures.vectorShapes.svg.TurtleCommand
import it.evadid.vm.code.defining.{BeDependency, BeUnknownDependency}
import it.evadid.vm.conversion.dependencies.KnownDependencies
import it.evadid.vm.conversion.expressionpython.BeExpressionToPythonString
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStatePythonStructured

/** Python declarations in the same UML model as Java; unfinished source still exposes its declarations. */
object PythonUmlDiagram {
  def visibility(name: String): UmlVisibility =
    if name.startsWith("__") && !name.endsWith("__") then UmlVisibility.Private
    else if name.startsWith("_") && !name.endsWith("__") then UmlVisibility.Protected
    else UmlVisibility.Public

  def apply(state: ProgrammingStatePythonStructured): UmlClassDiagram =
    UmlClassDiagram(((PythonMethodEditing.module -> state.mainScript) +: state.definedClasses.toVector).map((key, code) =>
      box(key, code, key == PythonMethodEditing.module, UmlClassOrigin.Program)))

  /** Read-only boxes of what a program uses without defining it ([[ExternalUmlClasses]]): the modules of the
    * libraries it calls (`turtle`) and its unknown dependency, so `turtle.spin` is an implied member of `turtle`.
    */
  def external(libraries: Seq[BeDependency], unknown: Option[BeUnknownDependency]): UmlClassDiagram =
    ExternalUmlClasses(libraries.flatMap(pythonLibraryViews).map((name, code) =>
      box(BeEntityName.fromCodeString(name), code, isModule = true, UmlClassOrigin.Library)), unknown)

  /** The Python modules a dependency provides, as declarations without bodies. */
  private def pythonLibraryViews(dependency: BeDependency): Seq[(String, String)] = dependency.structureName match {
    case KnownDependencies.turtle.structureName => Seq("turtle" -> TurtleCommand.turtleClass.methods.map { method =>
      val name = method.functionTypeInfo.displayName.universalInterpretation()
      s"def ${BeExpressionToPythonString.standardTurtleNames.getOrElse(name, name)}(${method.inputs.map(_.name.universalInterpretation()).mkString(", ")}): ..."
    }.mkString("\n"))
    case KnownDependencies.system.structureName => Seq("builtins" -> "def print(*values, file=None): ...")
    case KnownDependencies.fetchText.structureName => Seq("fetch_text" -> "def fetch_text(url: str) -> str: ...")
    case _ => Nil
  }

  /** The box of a module or class source: its functions in source order, overloads numbered by occurrence. */
  private def box(key: BeEntityName, code: String, isModule: Boolean, origin: UmlClassOrigin): UmlClass = {
    val name = key.spelling()
    val parsed = SourceParsing.parse(code, SourceLanguage.Python)
    val functions = PythonMethodEditing.functions(code, isModule).foldLeft(Vector.empty[UmlMember]) { (done, function) =>
      val parameters = function.parameters.filterNot(p => Set("self", "cls")(p.name)).map(_.text).mkString(", ")
      val result = function.returnType.filterNot(_ == "None").fold("")(": " + _)
      val visible = visibility(function.name)
      done :+ UmlMember(UmlEntityKey(name, Some(UmlMemberKey(UmlEntityKind.Function, function.name, done.count(_.name == function.name)))),
        s"${visible.symbol}${function.name}($parameters)$result", visible,
        isModule || function.decorators.exists(value => value == "@staticmethod" || value == "@classmethod"),
        function.decorators.contains("@abstractmethod"), function.code)
    }
    val attributes = parsed.syntax.toOption.toSeq.flatMap { syntax =>
      def visit(node: PyAST, classBody: Boolean): Seq[UmlMember] = node match {
        case assignment: PySimpleAssignment if (classBody && assignment.target.locationString.isEmpty) || assignment.target.locationString == List("self") =>
          val target = assignment.target
          val visible = visibility(target.identifier)
          val text = syntax.sourceMap.rangeOf(assignment).map(_.text(code).trim).getOrElse("")
          Seq(UmlMember(UmlEntityKey(name, Some(UmlMemberKey(UmlEntityKind.Attribute, target.identifier, 0))),
            s"${visible.symbol}${target.identifier}${target.typeHint.fold("")(hint => ": " + hint.typenameInCode)}",
            visible, classBody, false, text))
        case owner: PyClassDef => owner.block.statements.flatMap(visit(_, true))
        case function: PyFunctionDef if function.name == "__init__" => function.block.statements.flatMap(visit(_, false))
        case _: PyFunctionDef => Nil
        case other => other.getChildren().collect { case child: PyAST => child }.flatMap(visit(_, classBody))
      }
      visit(syntax.program, isModule)
    }.distinctBy(_.name)
    UmlClass(key, name, code, false, attributes, functions, origin)
  }
}
