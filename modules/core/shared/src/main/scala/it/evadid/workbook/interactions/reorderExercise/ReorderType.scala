package it.evadid.workbook.interactions.reorderExercise

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.core.util.io.Serializer
import upickle.default.*

sealed trait ReorderType  {

}

object ReorderType {

  case class CODELINES(associatedLanguage: ProgrammingLanguage) extends ReorderType

  case object BASIC_STRINGS extends ReorderType
  
  case object LANGUAGE_MAP_IDS extends ReorderType

  private val allKnownTypes: Seq[ReorderType] =
    List(BASIC_STRINGS, LANGUAGE_MAP_IDS) ++ AppLanguage.programmingLanguages.map(CODELINES(_))

  /** The persisted name; code lines name their language by its persisted name. */
  def label(reorderType: ReorderType): String = reorderType match {
    case BASIC_STRINGS => "BASIC_STRINGS"
    case LANGUAGE_MAP_IDS => "LANGUAGE_MAP_IDS"
    case CODELINES(language) => s"CODELINES(${AppLanguage.ser.serialize(language)})"
  }

  private[reorderExercise] val serializer: Serializer[ReorderType] = new Serializer[ReorderType] {

    override def serialize(obj: ReorderType): String = label(obj)

    override def deserialize(str: String): ReorderType = {
      allKnownTypes.find(label(_) == str)
        .getOrElse(throw new RuntimeException(s"Unknown reorder type: $str"))
    }
  }

  given rw: ReadWriter[ReorderType] = serializer.uPickleReadWrite

}