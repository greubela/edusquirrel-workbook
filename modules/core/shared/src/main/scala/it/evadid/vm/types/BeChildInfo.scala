package it.evadid.vm.types

import it.evadid.core.datastructures.tree.nodeImpl.NodeBasedTreePosition
import upickle.default.*
case class BeChildInfo(myRoleInParent: BeChildRole, myScope: BeScope) derives ReadWriter{

  override val toString: String = myRoleInParent.toString

}
