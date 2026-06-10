package it.evadid.core.datastructures.language

import it.evadid.core.util.io.Serializer

case class LanguageMapContentId(val languageMapId: String, val entryKey: String) {
  assert(languageMapId.trim.toLowerCase == languageMapId, s"languageMapId must be lowercase, but was: $languageMapId!")
  assert(entryKey.trim.toLowerCase == entryKey, s"entryKey must be lowercase, but was: $entryKey!")
  val fullId: String = languageMapId.toLowerCase + "/" + entryKey.toLowerCase

  override final val toString: String = s"ID(${fullId})"
}

object LanguageMapContentId {

  def apply(fullId: String): LanguageMapContentId = {
    val parts = fullId.split("/")
    if (parts.length != 2) throw new IllegalArgumentException(s"Invalid language map identifier: $fullId")
    LanguageMapContentId(parts(0).trim.toLowerCase, parts(1).trim.toLowerCase)
  }

  def serializer: Serializer[LanguageMapContentId] = new Serializer[LanguageMapContentId] {
    override def serialize(obj: LanguageMapContentId): String = s"ID(${obj.fullId})"

    override def deserialize(str: String): LanguageMapContentId = {
      if (str.startsWith("ID(") && str.endsWith(")")) {
        apply(str.substring(3, str.length - 1))
      } else {
        throw new IllegalArgumentException("cannot parse id from: " + str)
      }
    }
  }

}





