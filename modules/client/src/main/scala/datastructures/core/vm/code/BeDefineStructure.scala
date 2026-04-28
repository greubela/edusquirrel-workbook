package datastructures.core.vm.code

import datastructures.core.vm.code.defining.{BeDefineClass, BeDefineFunction, BeDefineVariable}

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