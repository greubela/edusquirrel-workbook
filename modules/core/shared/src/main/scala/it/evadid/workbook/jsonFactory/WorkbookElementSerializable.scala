package it.evadid.workbook.jsonFactory

import it.evadid.core.util.io.serializer.ConstructorLikeSerializer
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable.universalReader
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

  def tryFromString(str: String): Option[WorkbookElementSerializable] = try {
    Some(fromStringConstructorLike(str))
  } catch case _ => try {
    Some(read(str)(using regularSerializer))
  } catch case _ => {
    None
  }

  // Older workbooks stored registry entries as JSON strings; new ones store objects.
  val universalReader: Reader[WorkbookElementSerializable] = reader[ujson.Value].map {
    case ujson.Str(str) =>
      tryFromString(str).getOrElse(throw new IllegalArgumentException(s"Cannot parse workbook element: $str"))
    case json => read(json)(using regularSerializer)
  }

  /*


    def parse(element: WorkbookElementSerializable): WorkbookElement = WorkbookElementFactory.parse(element)

    def parseAll(elements: List[WorkbookElementSerializable]): List[WorkbookElement] = WorkbookElementFactory.parseAll(elements)

    val prefix = "WorkbookElementFactory"

    given rw: ReadWriter[WorkbookElementSerializable] = macroRW

    val serializer: Serializer[WorkbookElementSerializable] = Serializer.fromUpickleJson(rw)

    def base[T <: WorkbookElement](obj: T): WorkbookElementSerializable = {
      WorkbookElementSerializable(obj.elementId, obj.getClass.getSimpleName, Map())
    }

  */

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

  /*def getRegularSerializer(elementType: String): Serializer[WorkbookElement] = {
    val res = WorkbookElementSerializable.regularSerializer.get(elementType)
    res.getOrElse(throw SerializedException(s"No Serializer for '${elementType}' found in regularSerializerMap!"))
  }*/

  // Single Object
  def withElementAdded(key: String, value: String): WorkbookElementSerializable = {
    withMapAdded(Map(key -> writeJs[String](value)))
  }

  def withElementAddedAs[T](key: String, value: T)(implicit rw: Writer[T]): WorkbookElementSerializable = {
    withMapAdded(Map(key -> writeJs[T](value)))
  }

  // Multiple Objects
  def withElementsAdded(key: String, value: Seq[String]): WorkbookElementSerializable = {
    withMapAdded(Map(key -> writeJs[List[String]](value.toList)))
  }

  def withElementsAddedAs[T](key: String, elements: Seq[T])(implicit rw: Writer[T]): WorkbookElementSerializable = {
    withMapAdded(Map(key -> writeJs[List[T]](elements.toList)))
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
    } catch case (err: Throwable) => {
      throw SerializedException(s"Error at deserializing key $elementKey from Element ${elementId} (value ${elementValue.get})", Some(err))
    }
  }

  /*def getElementAs[T](elementKey: String)(serializer: Serializer[T]): T = {
    getElementAs(elementKey)(serializer.uPickleReadWrite)
  }*/

  def getOptionalElement(elementKey: String, defaultValue: String): String = {
    allConstructorFields.get(elementKey).map(_.str).getOrElse(defaultValue)
  }

  def getOptionalElementAs[T](elementKey: String, defaultValue: T)(implicit rw: Reader[T]): T = {
    val elementValue = allConstructorFields.get(elementKey)
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
    read[List[String]](allConstructorFields(elementKey))
  }

  def getElementsAs[T](elementKey: String)(implicit rw: Reader[T]): List[T] = {
    read[List[T]](allConstructorFields(elementKey))
  }
  /*def getElementsAs[T](elementKey: String)(serializer: Serializer[T]): List[T] = {
      getElementsAs[T](elementKey)(serializer.uPickleReadWrite)
  }*/

  // Other

  def getElementsAsSerializableElement(elementKey: String): List[WorkbookElementSerializable] = {
    getElementsAs(elementKey)(using universalReader)
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

  def getAndResolveWorkbookElements[T <: WorkbookElement](elementKey: String, parsedElements: Map[String, WorkbookElement]): List[T] = {
    val refs: List[WorkbookElementReference] = getElementsAs[WorkbookElementReference](elementKey)
    refs.map(curRef => resolveReference[T](curRef, parsedElements))
  }

}
