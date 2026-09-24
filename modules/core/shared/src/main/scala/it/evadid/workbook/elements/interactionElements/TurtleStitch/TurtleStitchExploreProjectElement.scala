package it.evadid.workbook.elements.interactionElements.TurtleStitch

import it.evadid.workbook.abstractions.WorkbookDisplayElement
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable

case class TurtleStitchExploreProjectElement(
                                              override val elementId: String,
                                              projectPathRelToResources: String,
                                              //    projectToDownload: FileDescription
                                            ) extends WorkbookDisplayElement {



}

object TurtleStitchExploreProjectElement {


}
