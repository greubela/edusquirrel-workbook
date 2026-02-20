package interactionPlugins.fileSubmission

import com.raquo.laminar.api.L.*
import contentmanagement.model.image.ImageDescription
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import workbook.model.info.WorkbookInfo
import workbook.workbookHtmlElements.abstractions.HtmlWorkbookElement
import workbook.workbookHtmlElements.basic.{HtmlContainerTitle, HtmlPlaintextInstructionElement}

import scala.::

object TurtleStitchFileUploadFactory {

  def createReprogramShapeExercise(
                                    workbookInfo: Var[WorkbookInfo],
                                    baseId: String,
                                    title: LanguageMap[HumanLanguage],
                                    expectedOutcome: ImageDescription
                                  ): List[HtmlWorkbookElement] = {

    val htmlTitleElement = HtmlContainerTitle(workbookInfo, title)

    val instr = HtmlPlaintextInstructionElement(workbookInfo, languageMapDefaultReprogramInstruction)

    val uploadLine = TurtleStitchUploadFileLine(workbookInfo, baseId, expectedOutcome)

    List(htmlTitleElement, instr, uploadLine)
  }

  val languageMapDefaultExerciseTitle: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Re-Create the Shape!",
    AppLanguage.German -> "Programmiere die Figur!"
  ))

  val languageMapDefaultReprogramInstruction: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Open TurtleStitch (https://www.turtlestitch.org/run) and program the shape shown on the right. Then, click on the file symbol (\uD83D\uDCDD) and save the file on your computer. Lastly, upload your file via the button on the left.",
    AppLanguage.German -> "Öffne TurtleStitch (https://www.turtlestitch.org/run) und programmiere die Figur im rechten Abschnitt. Klicke dann auf das Dateisymbol (\uD83D\uDCDD) und sichere deine Datei auf dem Computer. Lade sie zum Schluss mit dem Button im linken Abschnitt hoch."
  ))

  val languageMapUploadButtonCard: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Upload TurtleStitch XML-Project!",
    AppLanguage.German -> "TurtleStitch XML-Projekt hochladen!"
  ))
  val languageMapShowThumbnailPreview: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Uploaded Project (Thumbnail Preview)",
    AppLanguage.German -> "Hochgeladenes Projekt (Thumbnail)"
  ))

  val languageMapShowPentrailPreview: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Uploaded Project (Pentrail)",
    AppLanguage.German -> "Hochgeladenes Projekt (Pentrail)"
  ))

  val languageMapShowErrorPreview: LanguageMap[HumanLanguage]= LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Uploaded Project (with errors)",
    AppLanguage.German -> "Hochgeladenes Projekt (fehlerhaft)"
  ))

  val languageMapShowEmptyPreview: LanguageMap[HumanLanguage]= LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Uploaded Project (missing)",
    AppLanguage.German -> "Hochgeladenes Projekt (fehlt)"
  ))

  val languageMapShowEmptyDescription: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Once you upload your project, a preview of something you saw will be displayed here to let you verify that you uploaded the correct project.",
    AppLanguage.German -> "Sobald du dein Projekt hochgeladen hast, erscheint hier eine Vorschau mit der du dich vergewissern kannst, dass du auch das richtige Projekt hochgeladen hast"
  ))

  val languageMapShowExpected: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Desired Output",
    AppLanguage.German -> "Erwünschtes Ergebnis"
  ))

  val languageMapUploaddButton: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Upload TurtleStitch Project",
    AppLanguage.German -> "TurtleStitch Projekt Hochladen"
  ))

  val languageMapDownloadButton: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Download Shown Project",
    AppLanguage.German -> "Gezeigtes Projekt Herunterladen"
  ))

}
