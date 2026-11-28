package it.evadid.workbook.serialization

import it.evadid.core.util.io.serializer.ConstructorLikeSerializer
import it.evadid.core.util.io.ParsingContext
import scala.util.control.NonFatal
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.serialization.WorkbookElementSerializable.regularSerializer
import upickle.ReadWriter
import upickle.core.LinkedHashMap
import upickle.default.*

object WorkbookElementSerializable {

  def getSerializedVersion[T <: WorkbookElement](obj: T)(implicit rw: Writer[T]): WorkbookElementSerializable = {
    val map = ConstructorLikeSerializer.getAutoFieldsMap(obj)(using rw)
    WorkbookElementSerializable(obj.elementId, obj.getClass.getSimpleName, map)
  }

  def fromStringConstructorLike(str: String): WorkbookElementSerializable = {
    val read = ConstructorLikeSerializer.deserialize(str)
    val factory = WorkbookElementFactory.factoryFor(read.elementType)
    val fields = read.valuesWithOrder(factory.elementMapAndOrderForConstructorLike)
    val elementId = fields.getOrElse("elementId", throw new IllegalArgumentException("Missing elementId")).str
    WorkbookElementSerializable(elementId, read.elementType, fields)
  }

  given regularSerializer: ReadWriter[WorkbookElementSerializable] = macroRW

  //val knownSubtypeSerializer: Map[String, Serializer[WorkbookElement]] = Map()
}


case class WorkbookElementSerializable(
                                        elementId: String,
                                        elementType: String,
                                        allConstructorFields: Map[String, ujson.Value]
                                      ) {

  lazy val toRegularJson: String = {
    write(allConstructorFields)
  }

  // Single Object
  def withElementAdded(key: String, value: String): WorkbookElementSerializable = {
    withMapAdded(Map(key -> writeJs[String](value)))
  }

  def withElementAddedAs[T](key: String, value: T)(implicit rw: Writer[T]): WorkbookElementSerializable = {
    withMapAdded(Map(key -> writeJs[T](value)))
  }

  // Multiple Objects
  def withElementsAdded(key: String, value: Seq[String]): WorkbookElementSerializable = {
    withMapAdded(Map(key -> writeJs[Seq[String]](value.toList)))
  }

  def withElementsAddedAs[T](key: String, elements: Seq[T])(implicit rw: Writer[T]): WorkbookElementSerializable = {
    withMapAdded(Map(key -> writeJs[Seq[T]](elements.toList)))
  }

  // Map
  def withMapAdded(map: Map[String, ujson.Value]): WorkbookElementSerializable = {
    WorkbookElementSerializable(elementId, elementType, allConstructorFields ++ map)
  }

  // Other

  // Get Single

  def getElement(elementKey: String): String = allConstructorFields(elementKey).str

  def getElementAs[T](elementKey: String)(implicit rw: ReadWriter[T]): T = {
    val elementValue = allConstructorFields.get(elementKey)
    if (elementValue.isEmpty) {
      throw SerializedException(s"Key $elementKey not present in Element ${elementId}!")
    } else try {
      read(elementValue.get)
    } catch case NonFatal(err) => {
      throw SerializedException(s"Error parsing workbook element '$elementId' field '$elementKey': could not parse '${ParsingContext.preview(elementValue.get.toString)}' because: ${ParsingContext.reason(err)}", err)
    }
  }

  def getOptionalElement(elementKey: String, defaultValue: String): String = {
    allConstructorFields.get(elementKey).map(_.str).getOrElse(defaultValue)
  }

  def getOptionalElementAs[T](elementKey: String, defaultValue: T)(implicit rw: Reader[T]): T = {
    val elementValue = allConstructorFields.get(elementKey)
    if (elementValue.isEmpty) {
      defaultValue
    } else try {
      read(elementValue.get)
    } catch case NonFatal(err) => {
      throw SerializedException(s"Error parsing workbook element '$elementId' field '$elementKey': could not parse '${ParsingContext.preview(elementValue.get.toString)}' because: ${ParsingContext.reason(err)}", err)
    }
  }

  // Get Multiple
  def getElements(elementKey: String): Seq[String] = {
    read[Seq[String]](allConstructorFields(elementKey))
  }

  def getElementsAs[T](elementKey: String)(implicit rw: Reader[T]): Seq[T] = {
    read[Seq[T]](allConstructorFields(elementKey))
  }


  // Other

  def getElementsAsSerializableElement(elementKey: String): Seq[WorkbookElementSerializable] = {
    getElementsAs(elementKey)(using regularSerializer)
  }

  private def resolveReference[T <: WorkbookElement](ref: WorkbookElementReference, parsedElements: Map[String, WorkbookElement]): T = {
    val resolved: Option[WorkbookElement] = parsedElements.get(ref.referencedId)
    if (resolved.isEmpty) throw SerializedException(s"Cannot resolve required reference ${ref.referencedId} during construction!")
    else try resolved.get.asInstanceOf[T]
    catch case _ => throw SerializedException(s"Expected Type of WorkbookElement ${ref.referencedId} did not match (was ${resolved.get.getClass.getSimpleName})!")
  }

  def getAndResolveWorkbookElement[T <: WorkbookElement](elementKey: String, parsedElements: Map[String, WorkbookElement]): T = {
    resolveReference(getElementAs[WorkbookElementReference](elementKey), parsedElements)
  }

  def getAndResolveWorkbookElements[T <: WorkbookElement](elementKey: String, parsedElements: Map[String, WorkbookElement]): Seq[T] = {
    val refs: Seq[WorkbookElementReference] = getElementsAs[WorkbookElementReference](elementKey)
    refs.map(curRef => resolveReference[T](curRef, parsedElements))
  }

}
