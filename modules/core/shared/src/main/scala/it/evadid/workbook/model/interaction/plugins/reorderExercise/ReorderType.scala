package it.evadid.workbook.model.interaction.plugins.reorderExercise

import it.evadid.core.datastructures.language.AppLanguage
import it.evadid.core.datastructures.language.AppLanguage.ProgrammingLanguage
import it.evadid.core.util.io.Serializer

sealed trait ReorderType {

}

object ReorderType {

  case class CODELINES(associatedLanguage: ProgrammingLanguage) extends ReorderType

  case object BASIC_STRINGS extends ReorderType
  
  case object LANGUAGE_MAP_IDS extends ReorderType

  private val allKnownTypes: List[ReorderType] =
    List(BASIC_STRINGS) ++ AppLanguage.programmingLanguages.map(CODELINES(_))

  private[reorderExercise] val serializer: Serializer[ReorderType] = new Serializer[ReorderType] {

    override def serialize(obj: ReorderType): String = obj.toString

    override def deserialize(str: String): ReorderType = {
      allKnownTypes.find(_.toString == str)
        .getOrElse(throw new RuntimeException(s"Unknown reorder type: $str"))
    }
  }
}