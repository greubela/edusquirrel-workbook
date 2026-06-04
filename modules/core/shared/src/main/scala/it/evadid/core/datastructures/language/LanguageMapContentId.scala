package it.evadid.core.datastructures.language

case class LanguageMapContentId(languageMapId: String, entryKey: String) {

  val fullId: String = languageMapId + "/" + entryKey

}

object LanguageMapContentId {

  def apply(fullId: String): LanguageMapContentId = {
    val parts = fullId.split("/")
    if (parts.length != 2) throw new IllegalArgumentException(s"Invalid language map identifier: $fullId")
    LanguageMapContentId(parts(0), parts(1))
  }

}





