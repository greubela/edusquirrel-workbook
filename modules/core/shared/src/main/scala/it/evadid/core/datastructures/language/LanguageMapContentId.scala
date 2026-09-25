package it.evadid.core.datastructures.language

import it.evadid.core.util.io.Serializer
import it.evadid.core.util.io.serializer.DefaultSerializer
import upickle.ReadWriter

case class LanguageMapContentId(val languageMapId: String, val entryKey: String) {

  assert(languageMapId.trim.toLowerCase == languageMapId, s"languageMapId must be lowercase, but was: $languageMapId!")
  assert(entryKey.trim.toLowerCase == entryKey, s"entryKey must be lowercase, but was: $entryKey!")
  val fullId: String = languageMapId.toLowerCase + "/" + entryKey.toLowerCase

  override final val toString: String = s"ID(${fullId})"
}

object LanguageMapContentId {

  val serializerLangMapId: Serializer[LanguageMapContentId] = Serializer.constructorLikeSerializer("LangMapId", new Serializer[LanguageMapContentId]() {
    override def serialize(obj: LanguageMapContentId): String = obj.fullId

    override def deserialize(str: String): LanguageMapContentId = LanguageMapContentId(str)
  })

  given ReadWriter[LanguageMapContentId] = serializerLangMapId.uPickleReadWrite


  def apply(languageMapId: String, entryKey: String): LanguageMapContentId =
    new LanguageMapContentId(languageMapId.toLowerCase.trim, entryKey.toLowerCase.trim)

  def apply(fullId: String): LanguageMapContentId = {
    val parts = fullId.split("/")
    if (parts.length != 2) throw new IllegalArgumentException(s"Invalid language map identifier: $fullId")
    apply(parts(0), parts(1))
  }






}
