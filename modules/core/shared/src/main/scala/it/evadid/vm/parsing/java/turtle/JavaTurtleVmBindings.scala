package it.evadid.vm.parsing.java.turtle

import it.evadid.vm.code.defining.BeDefineVariable
import it.evadid.vm.code.usage.BeUseValue
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.parsing.java.turtle.{JavaTurtleResolution as R}
import it.evadid.vm.parsing.java.turtle.JavaTurtleSource.{Diagnostic, Problem}
import it.evadid.vm.types.{BeDataType, BeUseValueReference}

object JavaTurtleVmBindings {
  case class VariableBinding(variable: R.Variable, definition: Option[BeDefineVariable])
  case class MethodBinding(id: R.MethodId, originalName: String, name: BeEntityName,
      parameters: Vector[VariableBinding])

  // IDs and generated names belong to this source, not to a persisted Workbook element.
  final class Bindings private[JavaTurtleVmBindings](val source: R.ResolvedSource,
      val methods: Vector[MethodBinding], val variables: Vector[VariableBinding]) {
    private val methodById = methods.map(method => method.id -> method).toMap
    private val variableById = variables.map(binding => binding.variable.id -> binding).toMap

    val entryPoint: MethodBinding = methodById(source.entryPoint)

    def method(id: R.MethodId): Option[MethodBinding] = methodById.get(id)
    def variable(id: R.VariableId): Option[VariableBinding] = variableById.get(id)

    def definition(variable: R.Variable): Either[Diagnostic, BeDefineVariable] =
      variableById.get(variable.id).filter(_.variable == variable) match {
        case None => Left(Diagnostic(Problem.UnknownVariable,
          "This variable is not a binding in the current Java program.", None))
        case Some(binding) => binding.definition.toRight(Diagnostic(Problem.UnsupportedType,
          "The main argument array is not available as a turtle value.", None))
      }

    def reference(variable: R.Variable): Either[Diagnostic, BeUseValue] =
      definition(variable).map(value => BeUseValue(BeUseValueReference(value), None))
  }

  def bind(source: R.ResolvedSource): Bindings = {
    val variables = source.methods.flatMap { method =>
      (method.parameters ++ declarations(method.body)).sortBy(_.id.index).map { variable =>
        val dataType = variable.valueType match {
          case R.ValueType.IntValue => Some(BeDataType.Int)
          case R.ValueType.DoubleValue => Some(BeDataType.Numeric)
          case R.ValueType.BooleanValue => Some(BeDataType.Boolean)
          case R.ValueType.MainArguments => None
        }
        val name = BeEntityName.fromLiteral(s"java_variable_${variable.id.method.index}_${variable.id.index}")
        VariableBinding(variable, dataType.map(kind => BeDefineVariable(name, kind)))
      }
    }
    val byId = variables.map(binding => binding.variable.id -> binding).toMap
    val methods = source.methods.map { method =>
      MethodBinding(method.id, method.name, BeEntityName.fromLiteral(s"java_method_${method.id.index}"),
        method.parameters.map(parameter => byId(parameter.id)))
    }
    new Bindings(source, methods, variables)
  }

  private def declarations(block: R.Block): Vector[R.Variable] = {
    val variables = Vector.newBuilder[R.Variable]
    var pending = block.statements.toList
    while pending.nonEmpty do {
      val statement = pending.head
      pending = pending.tail
      statement match {
        case R.Declare(variable, _) => variables += variable
        case R.If(_, positive, negative) =>
          pending = positive.statements.toList ::: negative.toList.flatMap(_.statements) ::: pending
        case R.While(_, body) => pending = body.statements.toList ::: pending
        case R.For(init, _, update, body) =>
          pending = init.statements.toList ::: body.statements.toList ::: update.statements.toList ::: pending
        case R.Empty | R.Return | _: R.Assign | _: R.Evaluate | _: R.Call => ()
      }
    }
    variables.result()
  }
}
