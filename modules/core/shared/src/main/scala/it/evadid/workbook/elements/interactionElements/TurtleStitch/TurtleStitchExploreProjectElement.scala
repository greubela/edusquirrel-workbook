package it.evadid.workbook.elements.interactionElements.TurtleStitch

import it.evadid.workbook.abstractions.WorkbookDisplayElement
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable

case class TurtleStitchExploreProjectElement(
                                              override val elementId: String,
                                              projectPathRelToResources: String,
                                              //    projectToDownload: FileDescription
                                            ) extends WorkbookDisplayElement {


  override val toSerializableType: WorkbookElementSerializable = {
    toFactoryBase.withMapAdded(Map("projectPathRelToResources" -> projectPathRelToResources))
  }
}

object TurtleStitchExploreProjectElement {
  def fromFactory(factory: WorkbookElementSerializable): TurtleStitchExploreProjectElement = {
    TurtleStitchExploreProjectElement(factory.elementId, factory.getElementAsString("projectPathRelToResources"))
  }

}
