package workbook.workbookHtmlElements.abstractions

import workbook.workbookHtmlElements.container.HtmlExerciseContainer

trait HtmlExerciseFactory[T] {

  def createExercise(): HtmlExerciseContainer

  def getInteractions: List[WorkbookInteraction[T]]

}
