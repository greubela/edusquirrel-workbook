package contentmanagement.model.vm.types

import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition

case class BeChildPosition(roleInParent: BeChildRole, curScope: BeScope) {

  override val toString: String = roleInParent.toString

}
