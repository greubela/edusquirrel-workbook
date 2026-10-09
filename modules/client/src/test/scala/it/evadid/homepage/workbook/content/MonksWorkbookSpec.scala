package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.language.AppLanguage.German
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.interactionElements.slideshow.{Slideshow, SlideshowPanel}
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import munit.FunSuite

class MonksWorkbookSpec extends FunSuite {
  private def factory = CreateMonksWorkbook(null)

  test("all written PDF tasks have independent empty response fields with stable IDs") {
    val creator = factory
    val workbook = creator.createWorkbook
    val responses = workbook.allChildrenFullSubtree.collect { case text: TextInteraction => text }
    val expected = List("1b", "1c", "1d", "1e", "1f", "1g",
      "2b", "2c", "2d", "2e", "2f", "2g", "2h", "2i", "2j").map("monks-answer-" + _)
    assertEquals(responses.map(_.elementId), expected)
    assert(responses.forall(_.defaultValue.isEmpty))
    assertEquals(workbook.metadata.availableLanguages, List(German))
    assertEquals(workbook.sections.map(_.elementId), List("monks-story", "monks-recursion", "monks-counting"))
    assert(creator.createEverything.loadedWorkbook eq workbook)
    assertEquals(factory.createWorkbook, workbook)
    val ids = (workbook :: workbook.allChildrenFullSubtree).map(_.elementId)
    assertEquals(ids.distinct.size, ids.size)
  }

  test("theater and counting slides include both narrative-only and dialogue panels in reading order") {
    val slideshows = factory.createWorkbook.allChildrenFullSubtree.collect { case s: Slideshow => s }
    assertEquals(slideshows.map(_.elementId), List("monks-theater", "monks-counting-story"))
    assertEquals(slideshows.head.panels.map(_.elementId), CreateMonksWorkbook.theaterScenes.map(s => "monks-panel-" + s.id))
    assertEquals(slideshows(1).panels.map(_.elementId), List("monks-panel-family", "monks-panel-count"))
    assert(slideshows.head.panels.exists(_.isInstanceOf[SlideshowPanel.ImageSlide]))
    assert(slideshows.head.panels.exists(_.isInstanceOf[SlideshowPanel.TwoColumnImagePanel]))
    val sceneIds = CreateMonksWorkbook.theaterScenes.map(_.id)
    assert(sceneIds.indexOf("empty") < sceneIds.indexOf("firstreturn"))
    assert(sceneIds.indexOf("combine") < sceneIds.indexOf("sorted"))
    val images = (CreateMonksWorkbook.theaterScenes ++ CreateMonksWorkbook.countingScenes).map(_.imageNumber).toSet
    assertEquals(images, (1 to 18).toSet)
    val scenes = CreateMonksWorkbook.theaterScenes ++ CreateMonksWorkbook.countingScenes
    assertEquals(scenes.map(_.imageNumber).distinct.size, scenes.size)
    val imageKeys = slideshows.flatMap(_.panels).map {
      case panel: SlideshowPanel.ImageSlide => panel.image
      case panel: SlideshowPanel.TwoColumnImagePanel => panel.image
    }.map(_.asInstanceOf[it.evadid.workbook.elements.displayElements.ImageElement.LanguageMapBasedImageElement].languageMapContentId)
    assertEquals(imageKeys.distinct.size, 18)
  }

  test("the full digital workbook preserves panels and responses through JSON serialization") {
    val original = factory.createWorkbook
    val serializer = WorkbookElementFactory.serializerRegularJsonWorkbook
    val restored = serializer.deserialize(serializer.serialize(original))
    assertEquals(restored, original)
    assertEquals(restored.allContainedInteractions.map(_.elementId), original.allContainedInteractions.map(_.elementId))
  }
}
