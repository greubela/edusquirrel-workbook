package contentmanagement.model.vm.code

trait BeControlStructure extends BeExpression {

  def allPossibleBodies: List[BeExpression]

}
