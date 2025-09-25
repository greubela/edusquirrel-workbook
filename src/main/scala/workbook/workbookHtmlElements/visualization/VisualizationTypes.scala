package workbook.workbookHtmlElements.visualization

import workbook.model.exercise.{ExerciseContent, ExerciseSection}

sealed trait DependencyType {
  def isRequired: Boolean
}

object DependencyType {
  case object Required extends DependencyType { override val isRequired: Boolean = true }
  case object Recommended extends DependencyType { override val isRequired: Boolean = false }
}

final case class ExerciseBubbleLayout(
    exercise: ExerciseContent,
    width: Double,
    height: Double,
    relativeX: Double,
    relativeY: Double
)

final class SectionNode(val section: ExerciseSection, val index: Int) {
  var layer: Int = 0
  var order: Int = 0
  var width: Double = 0
  var height: Double = 0
  var x: Double = 0
  var y: Double = 0
  var bubbleLayouts: List[ExerciseBubbleLayout] = Nil
  var bubbleAreaTop: Double = 0
  var bubbleAreaHeight: Double = 0
}

final case class Edge(source: Int, target: Int, dependencyType: DependencyType)

final case class WorkbookLayout(nodes: List[SectionNode], edges: List[Edge], width: Double, height: Double)
