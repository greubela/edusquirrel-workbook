package contentmanagement.datastructures.tree

trait ExecutionContextInfo[P <: TreePosition, D, O]() {

  def curTraversalInformation: TraversalInformation[D, P]

  def accessOtherResult(otherPosition: TraversalInformation[D, P]): O

  def accessChildrenResults: List[O] = curTraversalInformation.traversalInfoForChildren.map(accessOtherResult)

  def accessParentResult: Option[O] = curTraversalInformation.traversalInfoForParent.map(accessOtherResult)
}