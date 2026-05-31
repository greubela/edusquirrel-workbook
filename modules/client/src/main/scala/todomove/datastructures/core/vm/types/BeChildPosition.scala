package todomove.datastructures.core.vm.types

import it.evadid.core.datastructures.tree.nodeImpl.NodeBasedTreePosition

case class BeChildPosition(roleInParent: BeChildRole, curScope: BeScope) {

  override val toString: String = roleInParent.toString

}
