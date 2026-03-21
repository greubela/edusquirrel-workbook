package content.EmbroideryWorkbook

import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import content.TestWorkbook.TestWorkbook
import contentmanagement.model.file.*
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import interactionPlugins.turtleStitchPlugin.TurtleStitchExploreProjectExercise
import interactionPlugins.turtleStitchPlugin.card.*
import org.scalajs.dom
import org.scalajs.dom.URL
import workbook.model.*
import workbook.model.abstractions.HtmlWorkbookElement
import workbook.model.info.{WorkbookConfig, WorkbookInfo}
import workbook.user.User
import workbook.htmlElements.basic.{HtmlContainerTitle, HtmlPlaintextInstructionElement, HtmlUnsafeHtmlInstructionElement}
import workbook.htmlElements.container.*
import workbook.htmlElements.interactions.HtmlBasicTextInteraction

class CreateEmbroideryWorkbook(fullScreenElement: HtmlFullScreenContainerElement) {

  private var id = 0

  private def nextId(): String = {
    id = id + 1
    s"turtlestitch-exercise-$id"
  }

  val defaultInfo = WorkbookInfo(List[HumanLanguage](AppLanguage.English, AppLanguage.German), fullScreenElement, WorkbookConfig(AppLanguage.German, None, User("TestUser", "dummy@test.de")), Map())
  val workbookInfoVar: Var[WorkbookInfo] = Var(defaultInfo)

  private def createFileDescription(filename: String): FileDescription = {
    FileDescription.relativeToResourceFolder("workbookresources/embroidery/existingProjects/" + filename + ".xml")
  }

  private def createTextInput(): HtmlWorkbookElement = {
    HtmlBasicTextInteraction(workbookInfoVar, nextId())
  }

  private def createExploreExerciseDownloadInteraction(filename: String): HtmlWorkbookElement = {
    TurtleStitchExploreProjectExercise.createElementLine(workbookInfoVar, createFileDescription(filename))
  }


  private def thirdExercise: HtmlExerciseContainer = {

    val elements: List[HtmlWorkbookElement] = List(
      HtmlContainerTitle(workbookInfoVar, "EmbroideryWorkbook/Ex3Title"),
      HtmlUnsafeHtmlInstructionElement(workbookInfoVar, "EmbroideryWorkbook/Ex3Instr1"),
      createExploreExerciseDownloadInteraction("updown_forward"),
      HtmlUnsafeHtmlInstructionElement(workbookInfoVar, "EmbroideryWorkbook/Ex3Instr2"),
      createTextInput(),
      HtmlUnsafeHtmlInstructionElement(workbookInfoVar, "EmbroideryWorkbook/Ex3Instr3"),
      createTextInput()
    )
    HtmlExerciseContainer(workbookInfoVar, elements.toList)
  }


  private def secondExercise: HtmlExerciseContainer = {



    val elements: List[HtmlWorkbookElement] = List(
      HtmlContainerTitle(workbookInfoVar, "EmbroideryWorkbook/Ex2Title"),
      HtmlUnsafeHtmlInstructionElement(workbookInfoVar, "EmbroideryWorkbook/Ex2Instr1"),
      createExploreExerciseDownloadInteraction("reset_forward"),
      HtmlUnsafeHtmlInstructionElement(workbookInfoVar, "EmbroideryWorkbook/Ex2Instr2"),
      createTextInput(),
      HtmlUnsafeHtmlInstructionElement(workbookInfoVar, "EmbroideryWorkbook/Ex2Instr3"),
      createTextInput(),
      HtmlUnsafeHtmlInstructionElement(workbookInfoVar, "EmbroideryWorkbook/Ex2Instr4"),
      createTextInput(),
    )
    HtmlExerciseContainer(workbookInfoVar, elements.toList)
  }

  private def firstExercise: HtmlExerciseContainer = {


    val elements: List[HtmlWorkbookElement] = List(
      HtmlContainerTitle(workbookInfoVar, "EmbroideryWorkbook/Ex1Title"),
      HtmlUnsafeHtmlInstructionElement(workbookInfoVar, "EmbroideryWorkbook/Ex1Instr1"),
      createExploreExerciseDownloadInteraction("simple_forward"),
      // createExploreExerciseDownloadInteraction("complex_example"),
      // createExploreExerciseDownloadInteraction("more_complex"),
      HtmlUnsafeHtmlInstructionElement(workbookInfoVar, "EmbroideryWorkbook/Ex1Instr2"),
      createTextInput(),
      HtmlUnsafeHtmlInstructionElement(workbookInfoVar, "EmbroideryWorkbook/Ex1Instr3"),
      HtmlUnsafeHtmlInstructionElement(workbookInfoVar, "EmbroideryWorkbook/Ex1Instr4"),
      createTextInput(),
      HtmlUnsafeHtmlInstructionElement(workbookInfoVar, "EmbroideryWorkbook/Ex1Instr5"),
    )

    HtmlExerciseContainer(workbookInfoVar, elements.toList)

  }


  def createWorkbook(): Workbook = {
    val firstSection = createFirstSection()
    val secondSection = TestWorkbook.createTestSection(workbookInfoVar)
    val res: Workbook = {
      new Workbook(workbookInfoVar,
        LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
          AppLanguage.English -> "Learn to Program with Embroidery Patterns",
          AppLanguage.German -> "Programmieren lernen mit der Stickmaschine"
        )),
        List(
          firstSection,
          secondSection
        ))
    }
    workbookInfoVar.update(oldVal => oldVal.copy(config = oldVal.config.copy(activeSection = Some(firstSection))))
    res
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
      workbookInfoVar,
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


