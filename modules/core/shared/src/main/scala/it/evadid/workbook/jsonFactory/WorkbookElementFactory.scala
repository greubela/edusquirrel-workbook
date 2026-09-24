package it.evadid.workbook.jsonFactory

import it.evadid.core.util.io.Serializer
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.abstractions.WorkbookElement
import it.evadid.workbook.elements.structureElements.Workbook

import scala.annotation.tailrec
import scala.collection.mutable

object WorkbookElementFactory {

  val workbookElementSerializer: Serializer[WorkbookElement] = new Serializer[WorkbookElement]() {
    override def serialize(obj: WorkbookElement): String = {
      WorkbookElementSerializable.serializer.serialize(obj.toSerializableType)
    }

    override def deserialize(str: String): WorkbookElement = {
      parse(WorkbookElementSerializable.serializer.deserialize(str))
    }
  }

  private lazy val knownFactoriesMap: Map[String, WorkbookElementFactory[? <: WorkbookElement]] = Map(
    Workbook.getClass.getSimpleName -> Workbook.factory
  )


  def parse(element: WorkbookElementSerializable, knownElements: Map[String, WorkbookElement] = Map()): WorkbookElement = {
    parseAll(List(element)).head
  }

  def parseAll(elementsInOrder: List[WorkbookElementSerializable], knownElements: Map[String, WorkbookElement] = Map()): List[WorkbookElement] = {
    parseAll(elementsInOrder, elementsInOrder, Map(), knownFactoriesMap)._1
  }

  def parseAllAsMap(elementsInOrder: List[WorkbookElementSerializable], knownElements: Map[String, WorkbookElement] = Map()): Map[String, WorkbookElement] = {
    parseAll(elementsInOrder, elementsInOrder, Map(), knownFactoriesMap)._2
  }

  @tailrec
  private def parseAll(
                        elementsInOrder: List[WorkbookElementSerializable],
                        open: List[WorkbookElementSerializable],
                        alreadyParsed: Map[String, WorkbookElement],
                        knownFactories: Map[String, WorkbookElementFactory[? <: WorkbookElement]]
                      ): (List[WorkbookElement], Map[String, WorkbookElement]) = {
    if (open.isEmpty) {
      val notYetParsed = open.filter(el => !alreadyParsed.contains(el.elementId))
      if (notYetParsed.nonEmpty) throw SerializedException(s"Open is empty but ${notYetParsed} elements were not parsed yet (${notYetParsed.map(_.elementId)}")
      else {
        val resList = elementsInOrder.map(el => alreadyParsed(el.elementId))
        val resMap = alreadyParsed ++ resList.map(el => el.elementId -> el).toMap
        (resList, resMap)
      }
    } else {
      val newlyFinished: mutable.HashMap[String, WorkbookElement] = mutable.HashMap[String, WorkbookElement]()
      val stillOpen: mutable.ListBuffer[WorkbookElementSerializable] = mutable.ListBuffer()
      val newlyProvided: mutable.ListBuffer[WorkbookElementSerializable] = mutable.ListBuffer()

      open.foreach(curOpenElement => {
        val factory = knownFactories.get(curOpenElement.elementType)
        if (factory.isEmpty) {
          throw SerializedException(s"No factory known for WorkbookElement with type ${curOpenElement.elementType}")
        } else {
          newlyProvided ++= factory.get.serializedElementContainsOtherSerializations(curOpenElement)
          if (factory.get.idsRequiredForDeserialization(curOpenElement).forall(alreadyParsed.keySet.contains(_))) {
            val parsed: WorkbookElement = factory.get.fromSerializedElement(curOpenElement, alreadyParsed)
            newlyFinished += parsed.elementId -> parsed
          } else {
            stillOpen += curOpenElement
          }
        }
      })

      if (stillOpen.nonEmpty && newlyFinished.isEmpty && newlyProvided.isEmpty) {
        throw SerializedException(s"Iteration with no progress, likely because of a cyclic dependency, stop parsing! (still open: ${open.map(_.elementId)})")
      } else {
        parseAll(elementsInOrder, stillOpen.toList ++ newlyProvided, alreadyParsed, knownFactories)
      }

    }
  }

}


trait WorkbookElementFactory[T <: WorkbookElement] {

  protected def toFactoryBase(element: T): WorkbookElementSerializable = WorkbookElementSerializable(
    element.elementId, this.getClass.getSimpleName, Map()
  )

  private def associatedSerializer(parsedElements: Map[String, WorkbookElement]): Serializer[T] = new Serializer[T]() {
    override def serialize(obj: T): String = {
      WorkbookElementSerializable.serializer.serialize((toSerializableElement(obj)))
    }

    override def deserialize(str: String): T = {
      fromSerializedElement(WorkbookElementSerializable.serializer.deserialize(str), parsedElements)
    }
  }

  def idsRequiredForDeserialization(element: WorkbookElementSerializable): Set[String]

  def idsRequiredForDeserialization(element: T): Set[String] = idsRequiredForDeserialization(element.toSerializableType)

  def serializedElementContainsOtherSerializations(element: WorkbookElementSerializable): Seq[WorkbookElementSerializable]

  def fromSerializedElement(element: WorkbookElementSerializable, parsedElements: Map[String, WorkbookElement]): T

  def toSerializableElement(element: T): WorkbookElementSerializable

  def toSerializableElementUnsafe(element: WorkbookElement): WorkbookElementSerializable = {
    toSerializableElement(element.asInstanceOf[T])
  }
}