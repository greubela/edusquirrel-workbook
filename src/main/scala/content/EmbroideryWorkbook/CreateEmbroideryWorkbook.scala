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
import workbook.model.info.{WorkbookConfig, WorkbookInfo}
import workbook.user.User
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement
import workbook.workbookHtmlElements.basic.{HtmlContainerTitle, HtmlPlaintextInstructionElement, HtmlUnsafeHtmlInstructionElement}
import workbook.workbookHtmlElements.container.*
import workbook.workbookHtmlElements.interactions.HtmlBasicTextInteraction

class CreateEmbroideryWorkbook(fullScreenElement: HtmlFullScreenElement) {

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

  private def createInstructions(maps: List[LanguageMap[HumanLanguage]]): List[HtmlWorkbookElement] =
    maps.map(HtmlUnsafeHtmlInstructionElement(workbookInfoVar, _))

  private def thirdExercise: HtmlExerciseContainer = {
    val title = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
      AppLanguage.English -> "Third time´s the charm",
      AppLanguage.German -> "Aller guten Dinge sind drei"
    ))

    val instr0: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "Download the following program and execute it (once).",
      AppLanguage.German -> "Lade das folgende Programm herunter und führe es (einmal) aus."
    ))

    val instr1: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "Describe what the commands pen up and pen down do.",
      AppLanguage.German -> "Beschreibe was die Befehle Stift hoch und Stift runter tun."
    ))

    val instr2: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "Change the numbers in the 'go to' command. Describe what happens and what the variables x and y are for.",
      AppLanguage.German -> "Ändere die Zahlen im 'Gehe zu' Befehl. Beschreibe, was passiert und wofür die Variablen x und y sind."
    ))

    val instructionElements = createInstructions(List(instr0, instr1, instr2))

    val elements: List[HtmlWorkbookElement] = List(
      HtmlContainerTitle(workbookInfoVar, title),
      instructionElements(0),
      createExploreExerciseDownloadInteraction("updown_forward"),
      instructionElements(1),
      createTextInput(),
      instructionElements(2),
      createTextInput()
    )
    HtmlExerciseContainer(workbookInfoVar, elements.toList)
  }


  private def secondExercise: HtmlExerciseContainer = {
    val title = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
      AppLanguage.English -> "A second program for variety",
      AppLanguage.German -> "Ein zweites Programm für Vielfalt"
    ))

    val instr0: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "Download the following program and execute it repeatedly.",
      AppLanguage.German -> "Lade das folgende Programm herunter und führe es mehrfach aus."
    ))

    val instr1: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "Describe what happened and esp. what differed.",
      AppLanguage.German -> "Beschreibe was passiert ist und insb. was anders war."
    ))

    val instr2: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "Change the number in the green command running stitch to different values and describe, how the result changes because of that.",
      AppLanguage.German -> "Ändere die Zahl im grünen Befehl Laufstich auf verschiedene Werte und beschreibe, wie sich das Ergebnis hierdurch verändert."
    ))

    val instr3: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "Remove the running stitch command and replace it with a cross stitch command. Also change the numbers in this commmand and describe, how the result changes because of that.",
      AppLanguage.German -> "Entferne den Befehl Laufstich und ersetze ihn mit einem Kreuzstich-Befehl. Ändere auch in diesem Befehl die Zahlen und beschreibe, wie sich das Ergebnis hierdurch verändert."
    ))

    val instructionElements = createInstructions(List(instr0, instr1, instr2, instr3))

    val elements: List[HtmlWorkbookElement] = List(
      HtmlContainerTitle(workbookInfoVar, title),
      instructionElements(0),
      createExploreExerciseDownloadInteraction("reset_forward"),
      instructionElements(1),
      createTextInput(),
      instructionElements(2),
      createTextInput(),
      instructionElements(3),
      createTextInput(),
    )
    HtmlExerciseContainer(workbookInfoVar, elements.toList)
  }

  private def firstExercise: HtmlExerciseContainer = {

    val title = LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(
      AppLanguage.English -> "A simple program to start",
      AppLanguage.German -> "Ein einfaches Programm zum Anfang"
    ))

    val instr1: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "Read the commands within the following Program and try to guess what kind of shape it creates.",
      AppLanguage.German -> "Lies die Befehle im folgendem Programm und vermute, wie die Figur aussieht, die hierdurch erstellt wird.",
    ))

    val instr2: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "Describe, how the shape created by the program will probably look like.",
      AppLanguage.German -> "Beschreibe, wie die durch das Programm erstellte Figur vermutlich aussieht.",
    ))

    val instr3: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "Verify your expectation. <br> <br> To do so, downlaod the program with the button above and open it in <a target=\"_blank\" href=\"https://www.turtlestitch.org/run\"> TurtleStitch </a> by clicking on the file symbol (\uD83D\uDCDD) and then 'open...'. Execute the program afterwards by clicking on the green flag.",
      AppLanguage.German -> "Überprüfe deine Vermutung. <br> <br> Lade hierfür zunächst das Programm mit dem obigen Knopf herunter und öffne es in <a target=\"_blank\" href=\"https://www.turtlestitch.org/run\"> TurtleStitch </a> indem du auf das Dateisymbol (\uD83D\uDCDD) klickst und 'Öffnen...' auswählst. Führe das Programm dann aus, indem du die grüne Fahne drückst.",
    ))

    val instr4: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "Below the result image, additional information are available. How many stitches and how much space is required for the shape?",
      AppLanguage.German -> "Unter dem Ergebnis werden Informationen angezeigt. Wie viele Stiche und wie viel Platz werden für die Figur benötigt?"
    ))

    val instr5: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
      AppLanguage.English -> "Execute the program again and then once more. Describe what happens.",
      AppLanguage.German -> "Führe das Programm erneut und dann noch mal aus. Beschreibe, was passiert."
    ))

    val instructionElements = createInstructions(List(instr1, instr2, instr3, instr4, instr5))

    val elements: List[HtmlWorkbookElement] = List(
      HtmlContainerTitle(workbookInfoVar, title),
      instructionElements(0),
      createExploreExerciseDownloadInteraction("simple_forward"),
     // createExploreExerciseDownloadInteraction("complex_example"),
     // createExploreExerciseDownloadInteraction("more_complex"),
      instructionElements(1),
      createTextInput(),
      instructionElements(2),
      instructionElements(3),
      createTextInput(),
      instructionElements(4),
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


