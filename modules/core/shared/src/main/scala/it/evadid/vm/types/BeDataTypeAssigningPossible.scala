package it.evadid.vm.types

import upickle.default.ReadWriter

sealed trait BeDataTypeAssigningPossible derives ReadWriter {
  def possibleWithoutSyntaxErrors: Boolean
  def resultingType: BeDataType 
}

case class AssigningPossibleWithSameType(override val resultingType: BeDataType) extends BeDataTypeAssigningPossible derives ReadWriter {
  val possibleWithoutSyntaxErrors: Boolean = true
}

case class AssigningPossibleWithImplicitCast(override val resultingType: BeDataType) extends BeDataTypeAssigningPossible derives ReadWriter {
  val possibleWithoutSyntaxErrors: Boolean = true
}

case class AssigningNotPossible() extends BeDataTypeAssigningPossible derives ReadWriter {
  val possibleWithoutSyntaxErrors: Boolean = false
  val resultingType: BeDataType = BeDataType.Error
}