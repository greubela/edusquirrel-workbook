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
    AppLanguage.German -> "Programmiere die Figur!",
    AppLanguage.French -> "Recrée la forme !",
    AppLanguage.Ukrainian -> "Відтвори фігуру!",
    AppLanguage.Russian -> "Воссоздай фигуру!",
    AppLanguage.Turkish -> "Şekli yeniden oluştur!",
    AppLanguage.Danish -> "Genskab formen!"
  ))

  val languageMapDefaultReprogramInstruction: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Open TurtleStitch (https://www.turtlestitch.org/run) and program the shape shown on the right. Then, click on the file symbol (\uD83D\uDCDD) and save the file on your computer. Lastly, upload your file via the button on the left.",
    AppLanguage.German -> "Öffne TurtleStitch (https://www.turtlestitch.org/run) und programmiere die Figur im rechten Abschnitt. Klicke dann auf das Dateisymbol (\uD83D\uDCDD) und sichere deine Datei auf dem Computer. Lade sie zum Schluss mit dem Button im linken Abschnitt hoch.",
    AppLanguage.French -> "Ouvre TurtleStitch (https://www.turtlestitch.org/run) et programme la forme affichée à droite. Ensuite, clique sur l'icône de fichier (\uD83D\uDCDD) et enregistre le fichier sur ton ordinateur. Enfin, téléverse ton fichier avec le bouton à gauche.",
    AppLanguage.Ukrainian -> "Відкрий TurtleStitch (https://www.turtlestitch.org/run) і запрограмуй фігуру, показану праворуч. Потім натисни на значок файлу (\uD83D\uDCDD) і збережи файл на своєму комп'ютері. Насамкінець завантаж свій файл кнопкою ліворуч.",
    AppLanguage.Russian -> "Открой TurtleStitch (https://www.turtlestitch.org/run) и запрограммируй фигуру, показанную справа. Затем нажми на значок файла (\uD83D\uDCDD) и сохрани файл на компьютере. В конце загрузи свой файл с помощью кнопки слева.",
    AppLanguage.Turkish -> "TurtleStitch'i (https://www.turtlestitch.org/run) aç ve sağda gösterilen şekli programla. Sonra dosya simgesine (\uD83D\uDCDD) tıkla ve dosyayı bilgisayarına kaydet. Son olarak dosyanı soldaki düğme ile yükle.",
    AppLanguage.Danish -> "Åbn TurtleStitch (https://www.turtlestitch.org/run), og programmér formen, der vises til højre. Klik derefter på filsymbolet (\uD83D\uDCDD), og gem filen på din computer. Til sidst uploader du filen via knappen til venstre."
  ))

  val languageMapUploadButtonCard: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Upload TurtleStitch XML-Project!",
    AppLanguage.German -> "TurtleStitch XML-Projekt hochladen!",
    AppLanguage.French -> "Téléverser le projet XML TurtleStitch !",
    AppLanguage.Ukrainian -> "Завантажити XML-проєкт TurtleStitch!",
    AppLanguage.Russian -> "Загрузить XML-проект TurtleStitch!",
    AppLanguage.Turkish -> "TurtleStitch XML projesini yükle!",
    AppLanguage.Danish -> "Upload TurtleStitch XML-projekt!"
  ))
  val languageMapShowThumbnailPreview: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Uploaded Project (Thumbnail Preview)",
    AppLanguage.German -> "Hochgeladenes Projekt (Thumbnail)",
    AppLanguage.French -> "Projet téléversé (aperçu miniature)",
    AppLanguage.Ukrainian -> "Завантажений проєкт (мініатюра)",
    AppLanguage.Russian -> "Загруженный проект (миниатюра)",
    AppLanguage.Turkish -> "Yüklenen proje (küçük önizleme)",
    AppLanguage.Danish -> "Uploadet projekt (miniaturevisning)"
  ))

  val languageMapShowPentrailPreview: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Uploaded Project (Pentrail)",
    AppLanguage.German -> "Hochgeladenes Projekt (Pentrail)",
    AppLanguage.French -> "Projet téléversé (Pentrail)",
    AppLanguage.Ukrainian -> "Завантажений проєкт (Pentrail)",
    AppLanguage.Russian -> "Загруженный проект (Pentrail)",
    AppLanguage.Turkish -> "Yüklenen proje (Pentrail)",
    AppLanguage.Danish -> "Uploadet projekt (Pentrail)"
  ))

  val languageMapShowErrorPreview: LanguageMap[HumanLanguage]= LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Uploaded Project (with errors)",
    AppLanguage.German -> "Hochgeladenes Projekt (fehlerhaft)",
    AppLanguage.French -> "Projet téléversé (avec des erreurs)",
    AppLanguage.Ukrainian -> "Завантажений проєкт (із помилками)",
    AppLanguage.Russian -> "Загруженный проект (с ошибками)",
    AppLanguage.Turkish -> "Yüklenen proje (hatalı)",
    AppLanguage.Danish -> "Uploadet projekt (med fejl)"
  ))

  val languageMapShowEmptyPreview: LanguageMap[HumanLanguage]= LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Uploaded Project (missing)",
    AppLanguage.German -> "Hochgeladenes Projekt (fehlt)",
    AppLanguage.French -> "Projet téléversé (manquant)",
    AppLanguage.Ukrainian -> "Завантажений проєкт (відсутній)",
    AppLanguage.Russian -> "Загруженный проект (отсутствует)",
    AppLanguage.Turkish -> "Yüklenen proje (eksik)",
    AppLanguage.Danish -> "Uploadet projekt (mangler)"
  ))

  val languageMapShowEmptyDescription: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Once you upload your project, a preview of something you saw will be displayed here to let you verify that you uploaded the correct project.",
    AppLanguage.German -> "Sobald du dein Projekt hochgeladen hast, erscheint hier eine Vorschau mit der du dich vergewissern kannst, dass du auch das richtige Projekt hochgeladen hast",
    AppLanguage.French -> "Dès que tu téléverses ton projet, un aperçu de ce que tu as vu s'affichera ici pour vérifier que tu as bien téléversé le bon projet.",
    AppLanguage.Ukrainian -> "Щойно ти завантажиш свій проєкт, тут з'явиться попередній перегляд того, що ти бачив(ла), щоб ти міг(могла) перевірити, що завантажено правильний проєкт.",
    AppLanguage.Russian -> "Как только ты загрузишь свой проект, здесь появится предварительный просмотр того, что ты видел(а), чтобы ты мог(ла) проверить, что загружен правильный проект.",
    AppLanguage.Turkish -> "Projeni yüklediğinde, doğru projeyi yüklediğini doğrulaman için burada gördüğün şeyin bir önizlemesi gösterilir.",
    AppLanguage.Danish -> "Når du har uploadet dit projekt, vises en forhåndsvisning af noget, du har set, så du kan bekræfte, at du har uploadet det rigtige projekt."
  ))

  val languageMapShowExpected: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Desired Output",
    AppLanguage.German -> "Erwünschtes Ergebnis",
    AppLanguage.French -> "Résultat attendu",
    AppLanguage.Ukrainian -> "Бажаний результат",
    AppLanguage.Russian -> "Ожидаемый результат",
    AppLanguage.Turkish -> "İstenen çıktı",
    AppLanguage.Danish -> "Ønsket resultat"
  ))

  val languageMapUploaddButton: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Upload TurtleStitch Project",
    AppLanguage.German -> "TurtleStitch Projekt Hochladen",
    AppLanguage.French -> "Téléverser le projet TurtleStitch",
    AppLanguage.Ukrainian -> "Завантажити проєкт TurtleStitch",
    AppLanguage.Russian -> "Загрузить проект TurtleStitch",
    AppLanguage.Turkish -> "TurtleStitch projesini yükle",
    AppLanguage.Danish -> "Upload TurtleStitch-projekt"
  ))

  val languageMapDownloadButton: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(
    AppLanguage.English -> "Download Shown Project",
    AppLanguage.German -> "Gezeigtes Projekt Herunterladen",
    AppLanguage.French -> "Télécharger le projet affiché",
    AppLanguage.Ukrainian -> "Завантажити показаний проєкт",
    AppLanguage.Russian -> "Скачать показанный проект",
    AppLanguage.Turkish -> "Gösterilen projeyi indir",
    AppLanguage.Danish -> "Download det viste projekt"
  ))

}
