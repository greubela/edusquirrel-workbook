package interactionPlugins.blockEnvironment.exercise

import com.raquo.laminar.api.L.*
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import datastructures.core.geometry.{Bounds, Point}
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import workbook.htmlElements.basic.{HtmlContainerTitle, HtmlPlaintextInstructionElement}
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.AllWorkbookInfo

import scala.concurrent.ExecutionContext

object ProgrammingExerciseFactory {


  private def expectedResultRendererConfig: BeRenderingConfig = BeRenderingConfig.default()

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

  def createTurtleProgrammingExercise(workbookInfo: AllWorkbookInfo, id: String, titleLanguageMapId: String, expectedSvgResult: AppSvgElement): List[HtmlWorkbookElement] = {

    val titleElement = HtmlContainerTitle(workbookInfo, titleLanguageMapId)

    val instructionElement = HtmlPlaintextInstructionElement(workbookInfo, workbookInfo.stringSignalFromLanguageMapId("BlockEditor/turtleProgrammingInstruction")(ExecutionContext.global))

    val interactionElement = TurtleProgrammingInteraction(workbookInfo, id, expectedSvgResult)

    List(titleElement, instructionElement, interactionElement)
  }


}
