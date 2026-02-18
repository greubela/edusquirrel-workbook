package interactionPlugins.blockEnvironment.exercise

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import contentmanagement.model.geometry.{Bounds, Point}
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import contentmanagement.webElements.svg.AppSvgElement
import contentmanagement.webElements.svg.builder.SvgPathBuilder
import interactionPlugins.blockEnvironment.config.BeRenderingConfig
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement
import workbook.workbookHtmlElements.basic.{HtmlBasicExerciseTitle, HtmlPlaintextInstructionElement}

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

  def createTurtleProgrammingExercise(workbookInfoVar: Var[WorkbookInfo], id: String, titleMap: LanguageMap[HumanLanguage], expectedSvgResult: AppSvgElement): List[HtmlWorkbookElement] = {

    val titleElement = HtmlBasicExerciseTitle(workbookInfoVar, titleMap)
    
    val instruction = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
      AppLanguage.English -> "Use Turtle Commands to program the Shape on the right!",
      AppLanguage.German -> "Programmiere die Form auf der rechten Seite nach!"
    ))
    val instructionElement = HtmlPlaintextInstructionElement(workbookInfoVar, instruction)

    val interactionElement = TurtleProgrammingInteraction(workbookInfoVar, id, expectedSvgResult)

    List(titleElement, instructionElement, interactionElement)
  }

}
