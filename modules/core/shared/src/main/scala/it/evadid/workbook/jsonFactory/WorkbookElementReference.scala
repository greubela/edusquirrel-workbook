package it.evadid.workbook.jsonFactory

import it.evadid.core.util.io.Serializer
import it.evadid.workbook.abstractions.WorkbookElement
import upickle.ReadWriter

object WorkbookElementReference {
  val serializer: Serializer[WorkbookElementReference] = Serializer.constructorLikeSerializer("ElementId", new Serializer[WorkbookElementReference]() {
    override def serialize(obj: WorkbookElementReference): String = {
      obj.referencedId + obj.referencedType.map(value => ", " + value + "").getOrElse("")
    }

    override def deserialize(str: String): WorkbookElementReference = {
      val parts = str.split(",").map(_.trim)
      if (parts.length == 1) WorkbookElementReference(parts(0), None)
      else if (parts.length == 2) WorkbookElementReference(parts(0), Option(parts(1)))
      else throw new IllegalArgumentException("wrong format!")
    }
  })

  given ReadWriter[WorkbookElementReference] = serializer.uPickleReadWrite

}

case class WorkbookElementReference(referencedId: String, referencedType: Option[String]) {
  def resolveWith(elements: List[WorkbookElement]): Option[WorkbookElement] = elements.find(_.elementId == referencedId)
}
