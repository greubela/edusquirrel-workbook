package content.plantworkshop.tasks

import com.raquo.laminar.api.L.*

/** Motivation and safety intro. */
object Task0_Motivation {
  def render(): HtmlElement = {
    div(
      h1("Was machen wir heute?"),
      div(
        className := "task-box motivation-box",
        h3("🌱 Willkommen zum Arduino Pflanzenworkshop!"),
        p(
          "Heute lernen wir, wie man eine automatische Bewässerungsanlage mithilfe von Arduino baut. ",
          "Mit diesem Mikrocontroller werden wir einen Feuchtigkeitssensor auslesen und eine Pumpe steuern, ",
          "damit unsere Pflanzen immer genau zum richtigen Zeitpunkt Wasser bekommen."
        ),
        p(strong("Das werdet ihr lernen:")),
        ul(
          li("Wie man Sensoren ausliest (Feuchtigkeit im Boden messen)"),
          li("Wie man elektrische Geräte steuert (Pumpe an/aus schalten)"),
          li("Wie man beides verbindet (automatische Bewässerung)")
        ),
        div(
          className := "info-box",
          strong("Hinweis: "),
          "Wir arbeiten mit Arduino, einer Plattform, welche es einfach macht, Hardware mit Code zu steuern. ",
          "Keine Sorge, wenn ihr noch nie programmiert habt, wir fangen ganz von vorne an! ",
          "Ihr habt die Wahl zwischen einem Anfängermodus mit Drag-and-Drop Codebausteinen und einem Fortgeschrittenenmodus, in dem ihr Codelückentext selbst ausfüllen könnt. ",
        )
      ),
      div(
        className := "safety-warning",
        h3("Sicherheitshinweise"),
        ul(
          li(strong("Wasser und Strom:"), " Achtet darauf, dass keine elektrischen Teile (außer dem Sensor) mit Wasser in Berührung kommen."),
          li(strong("Stromversorgung:"), " Verwendet nur die vorgesehenen Netzteile und Spannungen."),
          li(strong("Bei Problemen oder Unsicherheiten:"), " Fragt ruhig immer nach.")
        )
      )
    )
  }
}
