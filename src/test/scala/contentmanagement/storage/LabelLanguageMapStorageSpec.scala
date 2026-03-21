package contentmanagement.storage

import contentmanagement.model.file.FileDescription
import contentmanagement.model.language.AppLanguage
import munit.FunSuite

import scala.concurrent.ExecutionContext.Implicits.global

class LabelLanguageMapStorageSpec extends FunSuite {

  test("loadLanguageMaps groups entries across language files") {
    val files = List(
      FileDescription("basic-en.csv", "dataLoadingMap;\"[Data is loading]\"\nimageLoadingMap;\"[Image is loading]\"\n".getBytes("UTF-8")),
      FileDescription("basic-de.csv", "dataLoadingMap;\"[Daten werden geladen]\"\nimageLoadingMap;\"[Bild wird geladen]\"\n".getBytes("UTF-8"))
    )

    LabelLanguageMapStorage.loadLanguageMaps(files).map { languageMaps =>
      val byId = languageMaps.toMap
      assertEquals(byId("basic/dataLoadingMap").getInLanguage(AppLanguage.English), "[Data is loading]")
      assertEquals(byId("basic/dataLoadingMap").getInLanguage(AppLanguage.German), "[Daten werden geladen]")
      assertEquals(byId("basic/imageLoadingMap").getInLanguage(AppLanguage.English), "[Image is loading]")
      assertEquals(byId("basic/imageLoadingMap").getInLanguage(AppLanguage.German), "[Bild wird geladen]")
    }
  }

  test("parseLanguageMapCsv uses fs2-data to support escaped quotes and embedded newlines") {
    val file = FileDescription("basic-en.csv", Array.emptyByteArray)
    val csv =
      """simple;"value"
quoted;"with ""quotes"" inside"
multi;"line1
line2"
"""

    LabelLanguageMapStorage.parseLanguageMapCsv(csv, file).map { rows =>
      assertEquals(rows("simple"), "value")
      assertEquals(rows("quoted"), "with \"quotes\" inside")
      assertEquals(rows("multi"), "line1\nline2")
    }
  }
}
