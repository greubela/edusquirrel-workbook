package contentmanagement.datastructures.tree

trait TraversalInformation[D, P <: TreePosition] {

  def curPosition: P

  def tree: Tree[D, P]

  val parentValue: Option[D]
  val curValue: D
  val childrenValues: List[D]

  def traversalInfoForParent: Option[TraversalInformation[D, P]]

  def traversalInfoForChildren: List[TraversalInformation[D, P]]
}