package interactionPlugins.blockEnvironment.exercise

import com.raquo.laminar.api.L.*
import contentmanagement.model.geometry.{Bounds, Point}
import contentmanagement.model.language.AppLanguage
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import workbook.model.exercise.ExerciseContent

case class ProgrammingExercise(
                                id: String,
                                titleMap: Map[AppLanguage, String],
                                instructionMap: Map[AppLanguage, String],
                                expectedResult: AppSvgElement
                              ) extends ExerciseContent {


  override def estimatedTimeInMinutes: Double = 3
}

object ProgrammingExercise {

  private val expectedResultRendererConfig: BeRenderingConfig = BeRenderingConfig.default()

  private def polygonExpectedResult(
                                     points: List[Point[Double]],
                                     fillColor: String,
                                     strokeColor: String,
                                     strokeWidth: String
                                   ): AppSvgElement = {
    val builder = points.tail.foldLeft(SvgPathBuilder[Double](points.head)) { (acc, point) =>
      acc.lineToAbs(point)
    }

    val shape = builder.closePath().toFixedDimensionShape
    val shapeSize = shape.displaySize(expectedResultRendererConfig)
    val shapeBounds = Bounds(Point[Double](0, 0), shapeSize)

    shape
      .render(expectedResultRendererConfig, shapeBounds)
      .addMods(
        List(
          svg.fill := fillColor,
          svg.stroke := strokeColor,
          svg.strokeWidth := strokeWidth
        )
      )
  }

  val DefaultTriangleExpectedResult: AppSvgElement = polygonExpectedResult(
    points = List(
      Point[Double](100.0, 10.0),
      Point[Double](190.0, 180.0),
      Point[Double](10.0, 180.0)
    ),
    fillColor = "#bbdefb",
    strokeColor = "#1e88e5",
    strokeWidth = "6"
  )

  val DefaultRectangleExpectedResult: AppSvgElement = polygonExpectedResult(
    points = List(
      Point[Double](20.0, 20.0),
      Point[Double](220.0, 20.0),
      Point[Double](220.0, 140.0),
      Point[Double](20.0, 140.0)
    ),
    fillColor = "#c8e6c9",
    strokeColor = "#43a047",
    strokeWidth = "5"
  )

  val DefaultPentagonExpectedResult: AppSvgElement = polygonExpectedResult(
    points = List(
      Point[Double](96.0, 10.0),
      Point[Double](168.0, 66.0),
      Point[Double](138.0, 166.0),
      Point[Double](54.0, 166.0),
      Point[Double](24.0, 66.0)
    ),
    fillColor = "#ffe0b2",
    strokeColor = "#fb8c00",
    strokeWidth = "4"
  )
}
