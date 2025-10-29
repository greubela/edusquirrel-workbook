package contentmanagement.model.vm.types

import contentmanagement.datastructures.tree.nodeImpl.NodeBasedTreePosition

case class BeChildPosition(parentPosition: NodeBasedTreePosition, roleInParent: BeChildRole, curScope: BeScope) {


}
