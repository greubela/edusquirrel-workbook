package it.evadid.vm.types

import it.evadid.core.datastructures.tree.nodeImpl.NodeBasedTreePosition

case class BeChildInfo(myRoleInParent: BeChildRole, myScope: BeScope) {

  override val toString: String = myRoleInParent.toString

}
