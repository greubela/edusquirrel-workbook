package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import munit.FunSuite

class PlantWorkshopWorkbookRoundTripSpec extends FunSuite {
  test("plant workshop workbook survives serialization round trip") {
    val original = CreatePlantworkshopWorkbook(
      null.asInstanceOf[FullInfo]
    ).createWorkbook
    val serialized = WorkbookElementFactory.serializerRegularJsonWorkbook.serialize(original)
    val restored = WorkbookElementFactory.serializerRegularJsonWorkbook.deserialize(serialized)

    assertEquals(restored.workbookId, original.workbookId)
    assertEquals(restored.metadata.workbookTitle, original.metadata.workbookTitle)
    assertEquals(restored.metadata.availableLanguages, original.metadata.availableLanguages)
    assertEquals(restored.sections.map(_.sectionId), original.sections.map(_.sectionId))
    assertEquals(restored.allChildrenFullSubtree.map(_.elementId), original.allChildrenFullSubtree.map(_.elementId))
    assertEquals(
      WorkbookElementFactory.serializerRegularJsonWorkbook.serialize(restored),
      serialized
    )
  }
  test("plant workshop workbook survives constructor-format round trip") {
    val original = CreatePlantworkshopWorkbook(null.asInstanceOf[FullInfo]).createWorkbook
    val serializer = WorkbookElementFactory.serializerConstructorLike
    val serialized = serializer.serialize(original)
    val restored = serializer.deserialize(serialized)

    assertEquals(restored, original)
    assertEquals(serializer.serialize(restored), serialized)
  }

  test("all wiring slides group their image and restored description") {
    import it.evadid.workbook.elements.structureElements.ExerciseGroup
    import it.evadid.workbook.elements.displayElements.{WorkbookImageElement, TwoColumnPanel, LabeledWorkbookElement, DisplayLangMapContent}
    import it.evadid.workbook.elements.interactionElements.slideshow.Slideshow
    val workbook = CreatePlantworkshopWorkbook(null.asInstanceOf[FullInfo]).createWorkbook
    val slides = workbook.allChildrenFullSubtree.collect { case s: Slideshow => s }
    assertEquals(slides.map(_.panelSize), List(5, 6))
    val groups = slides.flatMap(_.childrenOfThisElement).map(_.asInstanceOf[ExerciseGroup])
    groups.zipWithIndex.foreach { case (group, index) =>
      val slide = index + 1
      assertEquals(group.elements.size, 2)
      assert(group.elements.head.isInstanceOf[WorkbookImageElement])
      if (Set(3, 4, 8).contains(slide)) {
        val columns = group.elements(1).asInstanceOf[TwoColumnPanel]
        val labels = List(columns.left, columns.right).map(_.asInstanceOf[LabeledWorkbookElement[?]])
        assertEquals(labels.map(_.label.contentId), List(LanguageMapContentId("PlantWorkshop/LLabel"), LanguageMapContentId("PlantWorkshop/RLabel")))
        assertEquals(labels.map(_.baseElement.asInstanceOf[DisplayLangMapContent].content),
          List(LanguageMapContentId(s"PlantWorkshop/wiringSlideTextL$slide"), LanguageMapContentId(s"PlantWorkshop/wiringSlideTextR$slide")))
      } else assert(group.elements(1).isInstanceOf[LabeledWorkbookElement[?]])
    }
    val ids = workbook.allChildrenFullSubtree.map(_.elementId)
    assertEquals(ids.distinct.size, ids.size)
  }

}
