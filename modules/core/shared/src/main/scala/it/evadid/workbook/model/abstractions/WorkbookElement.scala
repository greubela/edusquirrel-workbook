package it.evadid.workbook.model.abstractions

import it.evadid.workbook.model.interaction.WorkbookInteraction

trait WorkbookElement {

  val childrenOfThisElement: List[WorkbookElement] = List()

  lazy val allContainedInteractions: List[WorkbookInteraction[?]] =
    childrenOfThisElement.flatMap(_.allContainedInteractions) ++ this.match {
      case i: WorkbookInteraction[?] => List(i)
      case _ => List()
    }
  
}
