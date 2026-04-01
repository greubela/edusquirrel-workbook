package datastructures.core.vm.code

trait BeControlStructure extends BeExpression {

  def allPossibleBodies: List[BeExpression]

}
