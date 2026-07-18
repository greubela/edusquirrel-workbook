package it.evadid.vm.parsing.abstractions

import it.evadid.vm.parsing.abstractions.GenericAST.NamedElement

trait GenericAST {


  def allNamedElements(): Map[String, NamedElement] =
    traversePreOrderWithListener {
      case (ne: NamedElement) => Some(ne.name, ne.asInstanceOf[NamedElement])
    }.flatten.toMap

  def traversePreOrderWithListener[T](onNodeVisited: GenericAST => T): Set[T] = {
    Set[T](onNodeVisited(this)) ++ getChildren().flatMap(_.traversePreOrderWithListener(onNodeVisited))
  }

  def getChildren(): Seq[GenericAST] = Seq()
}

object GenericAST {

  trait NamedElement {
    def name: String
  }

  trait GenericASTListener {
    def onNodeVisited(node: GenericAST): Unit = {
    }

  }


}
