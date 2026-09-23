package it.evadid.workbook.elements.interactionElements.TurtleStitch

import it.evadid.workbook.abstractions.WorkbookDisplayElement
import it.evadid.workbook.jsonFactory.WorkbookElementFactory

case class TurtleStitchExploreProjectElement(
                                              override val elementId: String,
                                              projectPathRelToResources: String,
                                              //    projectToDownload: FileDescription
                                            ) extends WorkbookDisplayElement {


  override def toSerializableType: WorkbookElementFactory = {
    super.toFactoryBase.withMapAdded(Map("projectPathRelToResources" -> projectPathRelToResources))
  }
}

object TurtleStitchExploreProjectElement {
  def fromFactory(factory: WorkbookElementFactory): TurtleStitchExploreProjectElement = {
    TurtleStitchExploreProjectElement(factory.elementId, factory.getElementAsString("projectPathRelToResources"))
  }

}
