package datastructures.core.tree

trait TreeStructureAndExecutionContext[P <: TreePosition, D, O] extends TreeStructureContext[P, D] with TreeFunctorExecutionContext[P, D, O] {
  def accessChildrenResults: List[O]
  def accessParentResult: Option[O]
}

case class TreeStructureAndExecutionContextImpl[P <: TreePosition, D, O](structureContext: TreeStructureContext[P, D], executionContext: TreeFunctorExecutionContext[P, D, O]) extends TreeStructureAndExecutionContext[P, D, O] {

  override def curPosition: P = structureContext.curPosition

  override def tree: Tree[P, D] = structureContext.tree

  override def parentValue: Option[D] = structureContext.parentValue

  override def curValue: D = structureContext.curValue

  override def childrenValues: List[D] = structureContext.childrenValues

  override def traversalInfoForParent: Option[TreeStructureContext[P, D]] = structureContext.traversalInfoForParent

  override def traversalInfoForChildren: List[TreeStructureContext[P, D]] = structureContext.traversalInfoForChildren

  override def accessOtherResult(otherPosition: TreeStructureContext[P, D]): O = executionContext.accessOtherResult(otherPosition)

  override def accessChildrenResults: List[O] = structureContext.traversalInfoForChildren.map(executionContext.accessOtherResult)

  override def accessParentResult: Option[O] = structureContext.traversalInfoForParent.map(executionContext.accessOtherResult)

  override def traversalInfoForSiblingsInParent: List[TreeStructureContext[P, D]] = structureContext.traversalInfoForSiblingsInParent
}


trait TreeFunctorExecutionContext[P <: TreePosition, D, O]() {
  def accessOtherResult(otherPosition: TreeStructureContext[P, D]): O
}

trait TreeStructureContext[P <: TreePosition, D] {
  def curPosition: P

  def tree: Tree[P, D]

  def parentValue: Option[D]

  def curValue: D

  def childrenValues: List[D]

  def traversalInfoForSiblingsInParent: List[TreeStructureContext[P, D]]

  def traversalInfoForParent: Option[TreeStructureContext[P, D]]

  def traversalInfoForChildren: List[TreeStructureContext[P, D]]
}