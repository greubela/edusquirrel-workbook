package it.evadid.vm.parsing.abstractions

import it.evadid.vm.parsing.abstractions.GenericAST.GenericASTListener

trait GenericAST {

  def traversePreOrderWithListener(listener: GenericASTListener): Unit = {
    listener.onNodeVisited(this)
    getChildren().foreach(_.traversePreOrderWithListener(listener))
  }

  def getChildren(): Seq[GenericAST] = Seq()

}

object GenericAST {


  trait GenericASTListener {

    def onNodeVisited(node: GenericAST): Unit = {

    }


  }


}
