package workbook.model.abstractions

import workbook.htmlElements.container.HtmlExerciseContainer

trait HtmlExerciseFactory[T] {

  def createExercise(): HtmlExerciseContainer

  def getInteractions: List[WorkbookInteraction[T]]

}
