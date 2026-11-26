package it.evadid.vm.structure

import it.evadid.core.datastructures.uml.*
import it.evadid.vm.editing.PythonMethodEditing
import it.evadid.vm.parsing.source.{SourceParsing, PythonFunctionStructured}
import it.evadid.vm.parsing.source.SourceParsing.SourceLanguage
import it.evadid.vm.parsing.python.clean.model.PyAST.*
import it.evadid.vm.parsing.python.clean.model.PyAST
import it.evadid.vm.naming.BeEntityName
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStatePythonStructured

/** Python declarations in the same UML model as Java; unfinished source still exposes its declarations. */
object PythonUmlDiagram {
  def visibility(name: String): UmlVisibility =
    if name.startsWith("__") && !name.endsWith("__") then UmlVisibility.Private
    else if name.startsWith("_") && !name.endsWith("__") then UmlVisibility.Protected
    else UmlVisibility.Public

  def apply(state: ProgrammingStatePythonStructured): UmlClassDiagram = {
    val sources = (PythonMethodEditing.module -> state.mainScript) +: state.definedClasses.toVector
    val classes = sources.map { (key, code) =>
      val name = key.spelling()
      val isModule = key == PythonMethodEditing.module
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
      UmlClass(key, name, code, false, attributes, functions, UmlClassOrigin.Program)
    }
    UmlClassDiagram(classes)
  }
}
