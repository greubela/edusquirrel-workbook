package it.evadid.workbook.jsonFactory

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.jsonFactory.WorkbookElementSerializable.{serializerR}
import upickle.{ReadWriter, default, macroRW, readwriter}

object WorkbookElementSerializable {

  def parse(element: WorkbookElementSerializable): WorkbookElement = WorkbookElementFactory.parse(element)

  def parseAll(elements: List[WorkbookElementSerializable]): List[WorkbookElement] = WorkbookElementFactory.parseAll(elements)

  val prefix = "WorkbookElementFactory"

  private given refRW: default.ReadWriter[WorkbookElementReference] = macroRW
  //private given refRWL: default.ReadWriter[List[WorkbookElementReference]] = macroRW

  private given refRWL: ReadWriter[List[WorkbookElementReference]] =
    readwriter[List[WorkbookElementReference]].bimap[List[WorkbookElementReference]](_.toSeq, _.toList)

  private given facRW: default.ReadWriter[WorkbookElementSerializable] = macroRW

  given facRWL: default.ReadWriter[List[WorkbookElementSerializable]] =
    readwriter[Seq[WorkbookElementSerializable]].bimap[List[WorkbookElementSerializable]](_.toSeq, _.toList)

  val serializer: Serializer[WorkbookElementSerializable] = Serializer.fromUpickleJson(facRW)
  val serializerR: Serializer[WorkbookElementReference] = Serializer.fromUpickleJson(refRW)


}


case class WorkbookElementSerializable(
                                        elementId: String,
                                        elementType: String,
                                        additionalElements: Map[String, String]
                                      ) {

  def withElementAdded(key: String, value: String): WorkbookElementSerializable = {
    withMapAdded(Map(key -> value))
  }

  def withMapAdded(map: Map[String, String]): WorkbookElementSerializable = {
    WorkbookElementSerializable(elementId, elementType, additionalElements ++ map)
  }

  def withElementAdded[T](key: String, element: T)(implicit rw: ReadWriter[T]): WorkbookElementSerializable = {
    withElementAdded(key, element)(Serializer.fromUpickleJson(rw))
  }

  def withElementAdded[T](key: String, element: T)(serializer: Serializer[T]): WorkbookElementSerializable = {
    WorkbookElementSerializable(elementId, elementType, additionalElements ++ Map(key -> serializer.serialize(element)))
  }

  def withContentIdAdded(key: String, element: LanguageMapContentId): WorkbookElementSerializable = {
    withElementAdded(key, element)(DefaultSerializer.serializerLangMapId)
  }

  def withContentIdsAdded(key: String, element: List[LanguageMapContentId]): WorkbookElementSerializable = {
    withElementsAdded(key, element)(DefaultSerializer.serializerLangMapId)
  }

  def withSerializationsAdded(key: String, workbookElements: Seq[WorkbookElementSerializable]): WorkbookElementSerializable = {
    withElementAdded(key, workbookElements.toList)(summon[ReadWriter[Seq[WorkbookElementSerializable]]])
  }

  def withReferenceAdded(key: String, workbookElement: WorkbookElementReference): WorkbookElementSerializable = {
    withElementAdded(key, workbookElement)(serializerR)
  }

  def withReferencesAdded(key: String, workbookElement: Seq[WorkbookElementReference]): WorkbookElementSerializable = {
    withElementAdded(key, workbookElement)(summon[ReadWriter[Seq[WorkbookElementReference]]])
  }

  def withElementsAdded(key: String, value: Seq[String]): WorkbookElementSerializable = {
    withElementAdded(key, value)(summon[ReadWriter[Seq[String]]])
  }

  def withElementsAdded[T](key: String, values: Seq[T])(serializer: Serializer[T]): WorkbookElementSerializable = {
    withElementsAdded(key, values.map(serializer.serialize))
  }

  def getElements(elementKey: String): List[String] = {
    val rw = summon[ReadWriter[List[String]]]
    upickle.default.read[List[String]](getElementAsString(elementKey)).toList
  }

  def getElements[T](elementKey: String)(serializer: Serializer[T]): List[T] = {
    getElements(elementKey).map(serializer.deserialize)
  }

  def getElementAsString(elementKey: String): String = additionalElements(elementKey)

  def getOptionalElementAsString(elementKey: String, defaultValue: String): String = {
    additionalElements.getOrElse(elementKey, defaultValue)
  }

  def getOptionalElementAs[T](elementKey: String, defaultValue: T)(implicit rw: ReadWriter[T]): T = {
    getOptionalElementAs(elementKey, defaultValue)(Serializer.fromUpickleJson(rw))
  }

  def getOptionalElementAs[T](elementKey: String, defaultValue: T)(serializer: Serializer[T]): T = {
    val elementValue = additionalElements.get(elementKey)
    if (elementValue.isEmpty) {
      defaultValue
    } else try {
      serializer.deserialize(elementValue.get)
    } catch case (err: Throwable) => {
      throw SerializedException(s"Error at deserializing key ${elementKey} from Element ${elementId} (value ${elementValue.get})", Some(err))
    }
  }

  def getElementAs[T](elementKey: String)(serializer: Serializer[T]): T = {
    val elementValue = additionalElements.get(elementKey)
    if (elementValue.isEmpty) {
      throw SerializedException(s"Key ${elementKey} not present in Element ${elementId}!")
    } else try {
      serializer.deserialize(elementValue.get)
    } catch case (err: Throwable) => {
      throw SerializedException(s"Error at deserializing key ${elementKey} from Element ${elementId} (value ${elementValue.get})", Some(err))
    }
  }

  def getElementsAsSerializedElements(elementKey: String): List[WorkbookElementSerializable] = {
    getOptionalElementAs[Seq[WorkbookElementSerializable]](elementKey, List())(summon[ReadWriter[Seq[WorkbookElementSerializable]]]).toList
  }

  def getElementAsContentId(elementKey: String): LanguageMapContentId = {
    getElementAs[LanguageMapContentId](elementKey)(DefaultSerializer.serializerLangMapId)
  }

  def getElementAsContentIds(elementKey: String): List[LanguageMapContentId] = {
   getElements(elementKey)(DefaultSerializer.serializerLangMapId)
  }

  private def resolveReference[T <: WorkbookElement](ref: WorkbookElementReference, parsedElements: Map[String, WorkbookElement]): T = {
    val resolved: Option[WorkbookElement] = parsedElements.get(ref.referencedId)
    if (resolved.isEmpty) throw SerializedException(s"Cannot resolve required reference ${ref.referencedId} during construction!")
    else try resolved.get.asInstanceOf[T]
    catch case _ => throw SerializedException(s"Expected Type of WorkbookElement ${ref.referencedId} did not match (was ${resolved.get.getClass.getSimpleName})!")
  }

  def getAndResolveWorkbookElement[T <: WorkbookElement](elementKey: String, parsedElements: Map[String, WorkbookElement]): T = {
    resolveReference(getElementAsWorkbookReference(elementKey), parsedElements)
  }

  def getAndResolveWorkbookElements[T <: WorkbookElement](elementKey: String, parsedElements: Map[String, WorkbookElement]): List[T] = {
    val refs = getElementsAsWorkbookReferences(elementKey)
    refs.map(curRef => resolveReference[T](curRef, parsedElements))
  }

  def getElementAsWorkbookReference(elementKey: String): WorkbookElementReference = {
    getElementAs[WorkbookElementReference](elementKey)(serializerR)
  }

  def getElementsAsWorkbookReferences(elementKey: String): List[WorkbookElementReference] = {
    getOptionalElementAs[Seq[WorkbookElementReference]](elementKey, List())(summon[ReadWriter[Seq[WorkbookElementReference]]]).toList
  }


}
