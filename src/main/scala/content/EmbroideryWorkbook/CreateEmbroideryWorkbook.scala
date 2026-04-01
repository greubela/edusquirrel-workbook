package content.EmbroideryWorkbook

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import content.TestWorkbook.TestWorkbookFactory
import content.WorkbookFactory
import datastructures.core.language.{AppLanguage, HumanLanguage, LanguageMap}
import datastructures.web.file.FileDescription
import interactionPlugins.turtleStitchPlugin.TurtleStitchExploreProjectExercise
import interactionPlugins.turtleStitchPlugin.card.*
import org.scalajs.dom
import org.scalajs.dom.URL
import workbook.model.*
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.{AllWorkbookInfo, WorkbookConfig, WorkbookInfo}
import workbook.user.User
import workbook.htmlElements.basic.{HtmlContainerTitle, HtmlPlaintextInstructionElement, HtmlUnsafeHtmlInstructionElement}
import workbook.htmlElements.container.*
import workbook.htmlElements.interactions.HtmlBasicTextInteraction

case class CreateEmbroideryWorkbook(override val workbookInfo: AllWorkbookInfo) extends WorkbookFactory {

  override def createWorkbook: Workbook = {
    val firstSection = createFirstSection()
    val secondSection = TestWorkbookFactory.createTestSection(workbookInfo)
    val res: Workbook = {
      new Workbook(workbookInfo,
        LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
          AppLanguage.English -> "Learn to Program with Embroidery Patterns",
          AppLanguage.German -> "Programmieren lernen mit der Stickmaschine"
        )),
        List(
          firstSection,
          secondSection
        ))
    }
    workbookInfo.workbookInfoVar.update(oldVal => oldVal.copy(config = oldVal.config.copy(activeSection = Some(firstSection))))
    res
  }

  private def createFileDescription(filename: String): FileDescription = {
    FileDescription.relativeToResourceFolder("workbookresources/embroidery/existingProjects/" + filename + ".xml")
  }

  private def createTextInput(): HtmlWorkbookElement = {
    HtmlBasicTextInteraction(workbookInfo, nextId())
  }

  private def createExploreExerciseDownloadInteraction(filename: String): HtmlWorkbookElement = {
    TurtleStitchExploreProjectExercise.createElementLine(workbookInfo, createFileDescription(filename))
  }
  
  private def thirdExercise: HtmlExerciseContainer = {

    val elements: List[HtmlWorkbookElement] = List(
      HtmlContainerTitle(workbookInfo, "EmbroideryWorkbook/Ex3Title"),
      HtmlUnsafeHtmlInstructionElement(workbookInfo, "EmbroideryWorkbook/Ex3Instr1"),
      createExploreExerciseDownloadInteraction("updown_forward"),
      HtmlUnsafeHtmlInstructionElement(workbookInfo, "EmbroideryWorkbook/Ex3Instr2"),
      createTextInput(),
      HtmlUnsafeHtmlInstructionElement(workbookInfo, "EmbroideryWorkbook/Ex3Instr3"),
      createTextInput()
    )
    HtmlExerciseContainer(workbookInfo, elements.toList)
  }

  private def secondExercise: HtmlExerciseContainer = {

    val elements: List[HtmlWorkbookElement] = List(
      HtmlContainerTitle(workbookInfo, "EmbroideryWorkbook/Ex2Title"),
      HtmlUnsafeHtmlInstructionElement(workbookInfo, "EmbroideryWorkbook/Ex2Instr1"),
      createExploreExerciseDownloadInteraction("reset_forward"),
      HtmlUnsafeHtmlInstructionElement(workbookInfo, "EmbroideryWorkbook/Ex2Instr2"),
      createTextInput(),
      HtmlUnsafeHtmlInstructionElement(workbookInfo, "EmbroideryWorkbook/Ex2Instr3"),
      createTextInput(),
      HtmlUnsafeHtmlInstructionElement(workbookInfo, "EmbroideryWorkbook/Ex2Instr4"),
      createTextInput(),
    )
    HtmlExerciseContainer(workbookInfo, elements.toList)
  }

  private def firstExercise: HtmlExerciseContainer = {


    val elements: List[HtmlWorkbookElement] = List(
      HtmlContainerTitle(workbookInfo, "EmbroideryWorkbook/Ex1Title"),
      HtmlUnsafeHtmlInstructionElement(workbookInfo, "EmbroideryWorkbook/Ex1Instr1"),
      createExploreExerciseDownloadInteraction("simple_forward"),
      // createExploreExerciseDownloadInteraction("complex_example"),
      // createExploreExerciseDownloadInteraction("more_complex"),
      HtmlUnsafeHtmlInstructionElement(workbookInfo, "EmbroideryWorkbook/Ex1Instr2"),
      createTextInput(),
      HtmlUnsafeHtmlInstructionElement(workbookInfo, "EmbroideryWorkbook/Ex1Instr3"),
      HtmlUnsafeHtmlInstructionElement(workbookInfo, "EmbroideryWorkbook/Ex1Instr4"),
      createTextInput(),
      HtmlUnsafeHtmlInstructionElement(workbookInfo, "EmbroideryWorkbook/Ex1Instr5"),
    )

    HtmlExerciseContainer(workbookInfo, elements.toList)

  }



  private def createFirstSection(): WorkbookSection = {

    val exercises = List(
      firstExercise,
      secondExercise,
      thirdExercise,
      /* createExecuteProgramExercise(
         LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
           AppLanguage.English -> "The second program",
           AppLanguage.German -> "Das zweite Programm"
         )
         ),
         "simple_forward"
       )*/
    )

    WorkbookSection(
      workbookInfo,
      LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
        AppLanguage.English -> "Section 1: Introduction",
        AppLanguage.German -> "Abschnitt 1: Einarbeitung"
      )),
      exercises
    )

  }

  /*private def createExecuteProgramExercise(titleMap: LanguageMap[HumanLanguage], filename: String): HtmlExerciseContainer = {
    val tup = createImageAndUrl(filename)
    val res = TurtleStitchFileFactory.createExecuteProgramExercise(
      workbookInfoVar,
      nextId(),
      titleMap,
      tup._1,
      tup._2
    )
    HtmlExerciseContainer(workbookInfoVar, res.toList)
  }

  private def createProgrammingSubmissionExercise(titleMap: LanguageMap[HumanLanguage], destImg: ImageDescription): HtmlExerciseContainer = {
    val res = TurtleStitchFileFactory.createReprogramShapeExercise(
      workbookInfoVar,
      nextId(),
      titleMap,
      destImg
    )
    HtmlExerciseContainer(workbookInfoVar, res.toList)
  }*/

}
