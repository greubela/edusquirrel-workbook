package it.evadid.workbook.jsonFactory

import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.WorkbookElement
import upickle.ReadWriter
import upickle.default.*

object WorkbookElementSerializable {

  def parse(element: WorkbookElementSerializable): WorkbookElement = WorkbookElementFactory.parse(element)

  def parseAll(elements: List[WorkbookElementSerializable]): List[WorkbookElement] = WorkbookElementFactory.parseAll(elements)

  val prefix = "WorkbookElementFactory"

  given rw: ReadWriter[WorkbookElementSerializable] = macroRW

  val serializer: Serializer[WorkbookElementSerializable] = Serializer.fromUpickleJson(rw)
}


case class WorkbookElementSerializable(
                                        elementId: String,
                                        elementType: String,
                                        additionalElements: Map[String, ujson.Value]
                                      ) {

  // Single Object
  def withElementAdded(key: String, value: String): WorkbookElementSerializable = {
    withMapAdded(Map(key -> writeJs[String](value)))
  }

  def withElementAddedAs[T](key: String, value: T)(implicit rw: ReadWriter[T]): WorkbookElementSerializable = {
    withMapAdded(Map(key -> writeJs[T](value)))
  }

  // Multiple Objects
  def withElementsAdded(key: String, value: Seq[String]): WorkbookElementSerializable = {
    withMapAdded(Map(key -> writeJs[List[String]](value.toList)))
  }

  def withElementsAddedAs[T](key: String, elements: Seq[T])(implicit rw: ReadWriter[T]): WorkbookElementSerializable = {
    withMapAdded(Map(key -> writeJs[List[T]](elements.toList)))
  }

  // Map
  def withMapAdded(map: Map[String, ujson.Value]): WorkbookElementSerializable = {
    WorkbookElementSerializable(elementId, elementType, additionalElements ++ map)
  }

  // Other

  // Get Single

  def getElement(elementKey: String): String = additionalElements(elementKey).str

  def getElementAs[T](elementKey: String)(implicit rw: ReadWriter[T]): T = {
    val elementValue = additionalElements.get(elementKey)
    if (elementValue.isEmpty) {
      throw SerializedException(s"Key $elementKey not present in Element ${elementId}!")
    } else try {
      read(elementValue.get)
    } catch case (err: Throwable) => {
      throw SerializedException(s"Error at deserializing key $elementKey from Element ${elementId} (value ${elementValue.get})", Some(err))
    }
  }

  /*def getElementAs[T](elementKey: String)(serializer: Serializer[T]): T = {
    getElementAs(elementKey)(serializer.uPickleReadWrite)
  }*/

  def getOptionalElement(elementKey: String, defaultValue: String): String = {
    additionalElements.get(elementKey).map(_.str).getOrElse(defaultValue)
  }

  def getOptionalElementAs[T](elementKey: String, defaultValue: T)(implicit rw: ReadWriter[T]): T = {
    val elementValue = additionalElements.get(elementKey)
    if (elementValue.isEmpty) {
      defaultValue
    } else try {
      read(elementValue.get)
    } catch case (err: Throwable) => {
      throw SerializedException(s"Error at deserializing key $elementKey from Element $elementId (value ${elementValue.get})", Some(err))
    }
  }

  // Get Multiple
  def getElements(elementKey: String): List[String] = {
    read[List[String]](additionalElements(elementKey))
  }

  def getElementsAs[T](elementKey: String)(implicit rw: ReadWriter[T]): List[T] = {
    read[List[T]](additionalElements(elementKey))
  }
  /*def getElementsAs[T](elementKey: String)(serializer: Serializer[T]): List[T] = {
      getElementsAs[T](elementKey)(serializer.uPickleReadWrite)
  }*/

  // Other
  private def resolveReference[T <: WorkbookElement](ref: WorkbookElementReference, parsedElements: Map[String, WorkbookElement]): T = {
    val resolved: Option[WorkbookElement] = parsedElements.get(ref.referencedId)
    if (resolved.isEmpty) throw SerializedException(s"Cannot resolve required reference ${ref.referencedId} during construction!")
    else try resolved.get.asInstanceOf[T]
    catch case _ => throw SerializedException(s"Expected Type of WorkbookElement ${ref.referencedId} did not match (was ${resolved.get.getClass.getSimpleName})!")
  }

  def getAndResolveWorkbookElement[T <: WorkbookElement](elementKey: String, parsedElements: Map[String, WorkbookElement]): T = {
    resolveReference(getElementAs[WorkbookElementReference](elementKey), parsedElements)
  }

  def getAndResolveWorkbookElements[T <: WorkbookElement](elementKey: String, parsedElements: Map[String, WorkbookElement]): List[T] = {
    val refs: List[WorkbookElementReference] = getElementsAs[WorkbookElementReference](elementKey)
    refs.map(curRef => resolveReference[T](curRef, parsedElements))
  }


}
