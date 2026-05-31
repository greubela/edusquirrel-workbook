package todomove.datastructures.core.vm.types

sealed trait BeDataTypeAssigningPossible {
  def possibleWithoutSyntaxErrors: Boolean
  def resultingType: BeDataType 
}

case class AssigningPossibleWithSameType(override val resultingType: BeDataType) extends BeDataTypeAssigningPossible{
  val possibleWithoutSyntaxErrors: Boolean = true
}

case class AssigningPossibleWithImplicitCast(override val resultingType: BeDataType) extends BeDataTypeAssigningPossible{
  val possibleWithoutSyntaxErrors: Boolean = true
}

case class AssigningNotPossible() extends BeDataTypeAssigningPossible{
  val possibleWithoutSyntaxErrors: Boolean = false
  val resultingType: BeDataType = BeDataType.Error
}