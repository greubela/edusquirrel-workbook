package interactionPlugins.visualNovel

import contentmanagement.model.image.ImageDescription
import contentmanagement.model.image.ImageDescription.ServerImageDescription
import contentmanagement.model.language.{AppLanguage, HumanLanguage, LanguageMap}
import contentmanagement.storage.ImageStorage
import workbook.model.exercise.ExerciseContent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object VisualNovelContent {

  def monkContent: VisualNovelExercise = {

    val defaultImages = 1.to(5).map(curNr => ServerImageDescription("/resources/workbookresources/monks/Image0" + curNr + ".jpg"))
    val missImg = ServerImageDescription("./workbook/workbooks/monks/MissImg.png")
    val contentQuick: List[(ImageDescription, String)] = List(

      (defaultImages(0), "Erzähler: Und so begab es sich, dass ein Reisender – die Weisheit der Mönche suchend – sich zu den Mönchen von Mons Komputarius begab."),
      (defaultImages(1), "Nicht nach links und rechts schauend, schritt er direkt in den Tempel und sprach den Obermönch an"),
      (defaultImages(2), "Reisender (energisch): Meister... ich suche Lösungen!"),
      (defaultImages(3), "[Stille]"),
      (defaultImages(4), "Obermönch (langsam): das wundert mich nicht... wer so schnell geht, muss zwangsläufig an allen Lösungen vorbeilaufen..."),

      (defaultImages(2), "Erzähler: Der Mönch hat kaum geantwortet, da fährt der Reisende fort"),
      (defaultImages(2), "Reisender: MEISTER..! Meine Karten..!"),
      (missImg, "[Der Reisende wedelt mit den Karten herum]"),

      (defaultImages(2), "Reisender (energisch): ... Sie müssen Sortiert werden!"),
      (defaultImages(3), "[Stille]"),
      (defaultImages(4), "Obermönch (langsam): In der Tat!"),
      (defaultImages(3), "[Kurze Stille, der Reisende wartet darauf, ob noch etwas kommt]"),

      (defaultImages(2), "Reisender (energisch): Meister, ich weiß nicht wie"),
      (missImg, "[Längere Stille, der Obermönch mustert den Reisenden interessiert und aufmerksam]"),
      (defaultImages(4), "Obermönch (langsam, leicht seufzend): In der Tat!"),
      (defaultImages(3), "[Kurze Stille]"),

      (missImg, "Reisender (etwas langsamer): Könnt... könnt ihr mir helfen?"),
      (defaultImages(4), "Obermönch (langsam): In der Tat!"),

      (defaultImages(2), "Reisender (wieder energisch): So helft mir!"),
      (defaultImages(3), "[Kurze Stille]"),
      (defaultImages(4), "Obermönch (etwas schneller, aber immer noch sehr ruhig): Mein lieber Fremder... wie lange bist du hierhergereist?"),

      (missImg, "Reisender (etwas langsamer, leicht verwirrt): Mit dem Flug... 20 Stunden?"),
      (defaultImages(4), "Obermönch (ruhig): Und wie lange wirst du zurück Reisen"),

      (missImg, "Reisender (wieder langsamer, weniger verwirrt): vermutlich auch 20 Stunden?"),
      (defaultImages(4), "Obermönch (ruhig): Mein lieber Fremder... Unsere Wege kann ich dir zeigen. Aber ob du sie verstehst... das kann ich dir nicht versprechen... Schwer sind sie nicht..."),
      (defaultImages(4), "Obermönch (etwas langsamer): doch brauchen Sie eine Ruhe, die dir zu fehlen scheint, ..."),

      (missImg, "[Ein sehr junger Mönch kommt mit einem Tablett zum Reisenden und hält es ihm hin]"),
      (missImg, "Obermönch (immer noch ruhig, aber bestimmt): Leg die Karten auf das Tablett!"),

      (missImg, "[Der Reisende tut es, der junge Mönch nimmt das Tablett und hält es dem Meister hin]"),
      (defaultImages(4), "Obermönch (ruhig, etwas freundlicher): Pass gut auf und lerne!"),

      (missImg, "[Der Obermönch nimmt das Tablett und schaut die Karten lange an. Er greift nach der Karte mit dem größten Wert, nimmt sie in die Hand und hält das Tablett dem jungen Mönch hin]"),
      (missImg, "[Der junge Mönch nimmt das Tablett und reicht es dem nächsten Mönch]"),
      (missImg, "[Dieser schaut die Karten ebenfalls lange an, nimmt die Karte mit dem größten Wert und nickt]"),
      (missImg, "[Das wiederholt sich mit dem nächsten Mönch...]"),
      (missImg, "[Und dem nächsten...]"),
      (missImg, "[... bis das Tablett leer ist]"),
      (missImg, "[Der junge Mönch geht mit dem Tablett zum Reisenden und hält es ihm hin]"),
      (missImg, "[Der Reisende nimmt den Teller unsicher und schaut den Obermönch verwirrt an]"),

      (missImg, "Obermönch (etwas schneller): Mein lieber Fremder... du suchst Lösungen und doch siehst du eine Lösung nicht ein-mal dann, wenn sie direkt vor dir steht..."),
      (missImg, "[Der Reisende schaut den Obermönch weiter verwirrt an]"),
      (missImg, "Obermönch (wieder etwas schneller, leicht verärgert): Gib ihm das Tablett zurück!"),

      (missImg, "[Der Reisende gibt das Tablett an den jüngsten Mönch zurück. Dieser trägt es zurück zum letzten Mönch in der Reihe und hält es ihm hin]"),

      (missImg, "[Der letzte Mönch in der Reihe streicht einmal von links nach rechts über das Tablett und legt seine Karte auf die linke Seite des Tabletts]"),
      (missImg, "[Der jüngste Mönch geht mit dem Tablett zum zweitletzten Mönch in der Reihe und hält es diesem hin]"),
      (missImg, "[Dieser streicht über das Tablett und schiebt so die eine Karte von links nach rechts. Er legt seine Karte auf die linke Seite des Tabletts]"),
      (missImg, "[Der jüngste Mönch geht mit dem Tablett zum drittletzten Mönch in der Reihe und hält es diesem hin]"),
      (missImg, "[Dieser streicht über das Tablett und schiebt so beide vorherige Karten von links nach rechts. Er legt seine Karte auf die linke Seite des Tabletts]"),
      (missImg, "[... das geht so weiter bis der jüngste Mönch das Tablett dem Obermönch hinhält]"),

      (missImg, "[Der Obermönch streicht über das Tablett und schiebt so alle vorherigen Karten von links nach rechts. Er legt seine Karte auf die linke Seite des Tabletts]"),
      (missImg, "[Er nimmt das Tablett nun selbst in die Hand, geht zum Reisenden und hält es ihm hin]"),
      (missImg, "[Der Reisende schiebt die letzte Karte nach rechts und nimmt sie auf. Er nickt dabei. Als der Mönch das sieht strahlt er vor Freude]"),

      (defaultImages(4), "Obermönch (ruhig, glücklich): Mein lieber Fremder. Ich sehe, du kennst nun unsere Wege. Gehe hinaus und bediene dich unseres Wissens, wann immer es nötig ist... "),
      (defaultImages(4), "Obermönch (ruhig, glücklich): ... und hab keine Sorge: Es braucht keinen Tempel voll Mönche, um die Aufgabe zu erfüllen. Disziplin und Ruhe reichen völlig aus!"),

      (missImg, "Reisender (jetzt sehr ruhig, die Karten haltend): R: Ich verstehe vollkommen! Ich weiß, dass ich das auch allein schaffe. Ich habe zwar nicht genug Hände... aber auf meinem Tisch ist Platz genug. Danke, großer Meister!"),
      (defaultImages(4), "Obermönch (etwas schneller, noch glücklicher): Ich sehe, du hast vollkommen verstanden. Zieh nun hinfort... und gehe die Dinge in Zukunft etwas ruhiger an!"),
      (missImg, "[Der Reisende geht im normalen Tempo hinaus. Er schaut sich beim Laufen um und genießt die Umgebung]")
    )

    val panels = contentQuick.map(tup => {
      VisualNovelPanel(tup._1, "Created with Dall-E 3 by André Greubel", "[Todo: Add Alt Text for blind learners]", tup._2)
    })

    val cont = new ExerciseContent {
      override def titleMap: LanguageMap[HumanLanguage] = LanguageMap.universalMap[HumanLanguage]("The monks of mons computarius")

      override def id: String = "visual-novel-monks"

      override def estimatedTimeInMinutes: Double = 3
    }



    VisualNovelExercise(cont, panels)
  }
}


