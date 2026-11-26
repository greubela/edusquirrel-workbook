package it.evadid.vm.modules

import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable, BeIntrinsic}
import it.evadid.vm.naming.BeEntityName
import it.evadid.vm.types.BeDataType

/** Canonical static system functions, independent of Java/Python/Snap surface spelling. */
object SystemFunctionality {
  lazy val library: BeDefineClass = {
    val value = BeDefineVariable(BeEntityName.fromLiteral("value"), BeDataType.AnyType)
    val signatures = List("printToOut", "printToErr").map(name =>
      BeDefineClass.MethodSignature(BeEntityName.fromLiteral(name), List(value), None))
    val definition = BeDefineClass.withMethods(BeEntityName.fromLiteral("SystemFunctionality"), Nil, signatures)
    definition.copy(methods = definition.methods.map(method => method.copy(
      functionTypeInfo = method.functionTypeInfo.copy(funcType = BeDefineFunction.StaticMethod),
      intrinsic = Some(if method.functionTypeInfo.displayName.universalInterpretation() == "printToOut" then BeIntrinsic.Print else BeIntrinsic.PrintErr))))
  }

  def function(intrinsic: BeIntrinsic, arity: Int): BeDefineFunction =
    library.methods.find(_.intrinsic.contains(intrinsic)).get.copy(inputs = List.tabulate(arity)(index =>
      BeDefineVariable(BeEntityName.fromLiteral(s"arg$index"), BeDataType.AnyType)))
}
