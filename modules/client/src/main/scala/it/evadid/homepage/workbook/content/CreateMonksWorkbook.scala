package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.interactionElements.slideshow.{Slideshow, SlideshowPanel}
import it.evadid.workbook.elements.structureElements.Workbook

/** First digital edition of "Rekursion mit den Mönchen von Mons Komputarius" (29.04.2025).
  * Stable exercise IDs keep responses attached to their original PDF task numbers.
  */
case class CreateMonksWorkbook(override val fullInfo: FullInfo) extends WorkbookFactory {
  import CreateMonksWorkbook.*

  override val workbookId: String = "MonksWorkbook"
  override def availableLanguages: List[AppLanguage.HumanLanguage] = List(AppLanguage.German)

  private def key(name: String): String = "monksworkbook/" + name

  private def panel(scene: Scene): SlideshowPanel = {
    val image = imageResources(LanguageMapContentId(key("fileimage" + scene.imageNumber)))
    if (scene.twoColumns)
      SlideshowPanel.TwoColumnImagePanel(
        "monks-panel-" + scene.id, image,
        LanguageMapContentId(key("narratorlabel")), LanguageMapContentId(key("dialoguelabel")),
        LanguageMapContentId(key(scene.id + "narration")), LanguageMapContentId(key(scene.id + "dialogue")))
    else
      SlideshowPanel.ImageSlide(
        "monks-panel-" + scene.id, image,
        LanguageMapContentId(key(scene.id + "title")), LanguageMapContentId(key(scene.id + "narration")))
  }

  private def task(number: String): WorkbookElement =
    container(key("task" + number + "title"), List(
      instructionMarkdown(key("task" + number)),
      TextInteraction("monks-answer-" + number)))

  // Building once makes IDs and state references stable across createWorkbook/createEverything calls.
  override lazy val createWorkbook: Workbook = workbook(
    key("workbooktitle"),
    List(
      section("monks-story", key("storytitle"), List(
        instructionMarkdown(key("introduction")),
        container(key("task1atitle"), List(
          instructionMarkdown(key("task1a")),
          Slideshow("monks-theater", theaterScenes.map(panel)))))),
      section("monks-recursion", key("sortingtitle"),
        List(task("1b"), instructionMarkdown(key("recursionexplanation"))) ++
          List("1c", "1d", "1e", "1f", "1g").map(task)),
      section("monks-counting", key("countingtitle"),
        List(container(key("task2atitle"), List(
          instructionMarkdown(key("task2a")),
          Slideshow("monks-counting-story", countingScenes.map(panel))))) ++
          List("2b", "2c", "2d", "2e", "2f", "2g", "2h").map(task) ++
          List(instructionMarkdown(key("functionalexplanation"))) ++ List("2i", "2j").map(task))
    ),
    User.AndreGreubel)
}

object CreateMonksWorkbook {
  case class Scene(id: String, imageNumber: Int, twoColumns: Boolean = true)

  val theaterScenes: List[Scene] = List(
    Scene("arrival", 1, false),
    Scene("entrance", 2),
    Scene("impatience", 3),
    Scene("help", 5),
    Scene("journey", 4),
    Scene("patience", 4),
    Scene("tray", 6),
    Scene("largest", 7, false),
    Scene("smaller", 8, false),
    Scene("empty", 9),
    Scene("giveback", 9),
    Scene("firstreturn", 10, false),
    Scene("combine", 11, false),
    Scene("sorted", 12),
    Scene("understood", 12),
    Scene("farewell", 13))

  val countingScenes: List[Scene] = List(Scene("family", 14), Scene("count", 15, false))
}
