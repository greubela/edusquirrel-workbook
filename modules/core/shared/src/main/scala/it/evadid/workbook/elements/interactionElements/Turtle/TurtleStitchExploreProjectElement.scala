package it.evadid.workbook.elements.interactionElements.Turtle

import it.evadid.workbook.abstractions.WorkbookDisplayElement
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}

case class TurtleStitchExploreProjectElement(
                                              override val elementId: String,
                                              projectPathRelToResources: String,
                                              //    projectToDownload: FileDescription
                                            ) extends WorkbookDisplayElement {
  override val associatedFactory: WorkbookElementFactory[TurtleStitchExploreProjectElement] = TurtleStitchExploreProjectElement.factory

}

object TurtleStitchExploreProjectElement {

  val factory: SimpleWorkbookElementFactory[TurtleStitchExploreProjectElement] = new SimpleWorkbookElementFactory[TurtleStitchExploreProjectElement]() {
    override protected val constructorFieldOrder = List("elementId", "projectPathRelToResources")

    override def finishSerialization(baseElement: WorkbookElementSerializable, infoElement: TurtleStitchExploreProjectElement): WorkbookElementSerializable = {
      baseElement.withElementAdded("projectPathRelToResources", infoElement.projectPathRelToResources)
    }

    override def finishDeserialization(element: WorkbookElementSerializable): TurtleStitchExploreProjectElement = {
      TurtleStitchExploreProjectElement(element.elementId, element.getElementAs("projectPathRelToResources"))
    }
  }

}
