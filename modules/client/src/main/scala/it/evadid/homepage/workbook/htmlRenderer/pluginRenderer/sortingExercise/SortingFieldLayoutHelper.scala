package it.evadid.homepage.workbook.htmlRenderer.pluginRenderer.sortingExercise

object SortingFieldLayoutHelper {

  def resolvedColumns(fieldCount: Int): Int = fieldCount match {
    case n if n <= 1 => 1
    case 2 | 4 => 2
    case 3 | 5 | 6 => 2
    case _ => 3
  }

  def isFullWidthField(fieldIndex: Int, fieldCount: Int): Boolean =
    fieldCount == 3 && fieldIndex == 2
}
