package it.evadid.workbook.jsonFactory

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.interactionElements.TurtleStitch.TurtleStitchRecreateShapeInteraction
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.{refRW, refRWL}
import upickle.{ReadWriter, default, macroRW}

object WorkbookElementFactory {

  val knownFactories: Map[String, WorkbookElementFactory => WorkbookElement] = Map(
    TurtleStitchRecreateShapeInteraction.getClass.getSimpleName -> TurtleStitchRecreateShapeInteraction.fromFactory
  )

  val prefix = "WorkbookElementFactory"

  /* val serializerPretty: Serializer[WorkbookElementFactory] = new Serializer[WorkbookElementFactory] {

   override def serialize(obj: WorkbookElementFactory): String = {
      val keyValuePairs = obj.additionalElements.toList.map(el => el._1 + " -> " + el._2)
      val mapString = keyValuePairs.mkString(", ")
      s"${prefix}(${obj.elementType},${obj.elementId}):${mapString})"
    }

    override def deserialize(str: String): WorkbookElementFactory = {
      val trimmed = str.trim
      if (trimmed.length <= prefix.length + 1 || !trimmed.startsWith(prefix) || !trimmed.endsWith(")"))
        throw SerializedException(s"WorkbookElementFactory json should start with ${prefix}")
      else {
        val withoutPrefix = trimmed
          .substring(0, trimmed.length - 1)
          .substring(prefix.length + 1, trimmed.length - 2)

      }
    }
  }*/


  private val refRW: default.ReadWriter[WorkbookElementReference] = macroRW
  private val refRWL: default.ReadWriter[List[WorkbookElementReference]] = macroRW

  val facRW: default.ReadWriter[WorkbookElementFactory] = macroRW
  val serializer: Serializer[WorkbookElementFactory] = Serializer.fromUpickleJson(facRW)
}


case class WorkbookElementFactory(
                                   elementId: String,
                                   elementType: String,
                                   additionalElements: Map[String, String]
                                 ) {

  def withElementAdded(key: String, value: String): WorkbookElementFactory = {
    withMapAdded(Map(key -> value))
  }

  def withMapAdded(map: Map[String, String]): WorkbookElementFactory = {
    WorkbookElementFactory(elementId, elementType, additionalElements ++ map)
  }

  def withElementAdded[T](key: String, element: T)(implicit rw: ReadWriter[T]): WorkbookElementFactory = {
    withElementAdded(key, element)(Serializer.fromUpickleJson(rw))
  }

  def withElementAdded[T](key: String, element: T)(serializer: Serializer[T]): WorkbookElementFactory = {
    WorkbookElementFactory(elementId, elementType, additionalElements ++ Map(key -> serializer.serialize(element)))
  }

  def withContentIdAdded(key: String, element: LanguageMapContentId): WorkbookElementFactory = {
    withElementAdded(key, element)(LanguageMapContentId.serializer)
  }

  def withElementsAdded(key: String, workbookElements: Seq[WorkbookElement]): WorkbookElementFactory = {
    withReferencesAdded(key, workbookElements.map(_.asRef))
  }

  def withElementAdded(key: String, workbookElement: WorkbookElement): WorkbookElementFactory = {
    withReferenceAdded(key, workbookElement.asRef)
  }

  def withReferenceAdded(key: String, workbookElement: WorkbookElementReference): WorkbookElementFactory = {
    withElementAdded(key, workbookElement)(refRW)
  }

  def withReferencesAdded(key: String, workbookElement: Seq[WorkbookElementReference]): WorkbookElementFactory = {
    withElementAdded(key, workbookElement.toList)(refRWL)
  }


  def getElementAsString(elementKey: String): String = additionalElements(elementKey)

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

  def getElementAsContentId(elementKey: String): LanguageMapContentId = {
    getElementAs[LanguageMapContentId](elementKey)(LanguageMapContentId.serializer)
  }


}
