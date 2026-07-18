package it.evadid.vm.code

import it.evadid.vm.code.defining.*

trait BeDefineStructure extends BeExpression {

  def definedClasses: List[BeDefineClass] = List()

  def definedFunctions: List[BeDefineFunction] = List()

  def definedVariables: List[BeDefineVariable] = List()

  def allDefinedStructures: List[BeDefineStructure] = definedClasses ++ definedFunctions ++ definedVariables

}

object BeDefineStructure {

  lazy val defineBasicFunctions: BeDefineStructure = ???
  lazy val defineDefaultOperators: BeDefineStructure = ???

  lazy val defineTurtleEnvironment: BeDefineStructure = ???
  lazy val defineRobotEnvironment: BeDefineStructure = ???

}