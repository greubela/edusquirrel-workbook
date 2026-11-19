package it.evadid.vm.code.usage

import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.defining.BeDefineFunction
import it.evadid.vm.code.defining.BeDefineFunction.*
import upickle.default.*

/** The declaration owns names and signature; only instance calls carry a receiver. */
sealed trait BeCallTarget derives ReadWriter {
  def definition: BeDefineFunction
  def receiver: Option[BeExpression] = this match {
    case BeCallTarget.InstanceMethod(value, _) => Some(value)
    case _ => None
  }
}
object BeCallTarget {
  case class Function(definition: BeDefineFunction) extends BeCallTarget derives ReadWriter {
    require(definition.functionTypeInfo.isMethodInClass.isEmpty, "A free-function call cannot target a class member.")
  }
  case class InstanceMethod(instance: BeExpression, definition: BeDefineFunction) extends BeCallTarget derives ReadWriter {
    require(definition.functionTypeInfo.funcType == BeDefineFunction.Method(), "An instance call requires an instance-method declaration.")
    owner(definition)
  }
  case class StaticMethod(definition: BeDefineFunction) extends BeCallTarget derives ReadWriter {
    require(definition.functionTypeInfo.funcType == BeDefineFunction.StaticMethod(), "A static call requires a static-method declaration.")
    owner(definition)
  }
  case class Constructor(definition: BeDefineFunction) extends BeCallTarget derives ReadWriter {
    require(definition.functionTypeInfo.funcType == BeDefineFunction.Constructor(), "A constructor call requires a constructor declaration.")
    owner(definition)
  }

  def forDefinition(definition: BeDefineFunction): BeCallTarget = definition.functionTypeInfo.funcType match {
    case BeDefineFunction.Method() => InstanceMethod(BeCurrentInstance(owner(definition)), definition)
    case BeDefineFunction.StaticMethod() => StaticMethod(definition)
    case BeDefineFunction.Constructor() => Constructor(definition)
    case _ => Function(definition)
  }
  private def owner(definition: BeDefineFunction) = definition.functionTypeInfo.isMethodInClass
    .getOrElse(throw IllegalArgumentException("A method or constructor requires an owning class.")).name
}
