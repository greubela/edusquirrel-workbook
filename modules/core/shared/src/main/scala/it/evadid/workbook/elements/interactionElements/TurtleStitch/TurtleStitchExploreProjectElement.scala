package it.evadid.workbook.elements.interactionElements.TurtleStitch

import it.evadid.workbook.abstractions.WorkbookDisplayElement
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable

case class TurtleStitchExploreProjectElement(
                                              override val elementId: String,
                                              projectPathRelToResources: String,
                                              //    projectToDownload: FileDescription
                                            ) extends WorkbookDisplayElement {
  override val associatedFactory = it.evadid.workbook.jsonFactory.WorkbookElementFactory.unsupported[this.type](this.getClass.getSimpleName)



}

object TurtleStitchExploreProjectElement {


}
