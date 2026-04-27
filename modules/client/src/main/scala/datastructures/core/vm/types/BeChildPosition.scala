package datastructures.core.vm.types

import datastructures.core.tree.nodeImpl.NodeBasedTreePosition

case class BeChildPosition(roleInParent: BeChildRole, curScope: BeScope) {

  override val toString: String = roleInParent.toString

}
