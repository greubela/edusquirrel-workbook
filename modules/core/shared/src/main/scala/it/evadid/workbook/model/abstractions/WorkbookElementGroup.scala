package it.evadid.workbook.model.abstractions

import it.evadid.workbook.model.abstractions.*


trait WorkbookElementGroup[T <: WorkbookElement] extends WorkbookElement {
 
  def groupElements: List[T]

  override lazy val childrenOfThisElement: List[WorkbookElement] = groupElements

  def groupType: Option[WorkbookGroupType] = None
}

object WorkbookElementGroup {

  private case class BasicWorkbookElementGroup(override val groupElements: List[WorkbookElement], override val groupType: Option[WorkbookGroupType]) extends WorkbookElementGroup[WorkbookElement] {

  }

  def apply(groupElements: List[WorkbookElement], groupType: Option[WorkbookGroupType] = None): WorkbookElementGroup[WorkbookElement] =
    BasicWorkbookElementGroup(groupElements, groupType)

}

