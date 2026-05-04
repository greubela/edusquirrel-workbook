package content.plantworkshop.tasks

import com.raquo.laminar.api.L.*

/** Component checklist and build guide. */
object Task1_ComponentChecklist {
  private val checkedItems: Var[Set[String]] = Var(Set.empty)
  private val buildSteps = (1 to 11).map(i => s"../../resources/img/plantworkshop/schaltkreis/Plant%20conv%20$i.png").toList
  private val currentBuildStep: Var[Int] = Var(0)
  private val showWiringDetails = false

  def render(): HtmlElement = {
    div(
      h1("Bauteile & Aufbau"),
      div(
        className := "task-box",
        h3("Checkliste für die Bauteile"),
        p("Überprüft, ob ihr alle Teile habt:"),
        div(
          className := "component-checklist",
          checklistItem("Arduino Board", "Über dieses Mikrocontroller-Board werden wir mithilfe von Code die Sensoren und Pumpe steuern"),
          checklistItem("Feuchtigkeitssensor", "Misst, wie nass die Erde ist"),
          checklistItem("Wasserpumpe", "Pumpt Wasser zur Pflanze"),
          checklistItem("Relais-Modul", "Schaltet die Pumpe an/aus"),
          checklistItem("Netzteil", "Stromversorgung für die Pumpe"),
          checklistItem("Drähte", "Für die Verbindung zwischen Relais, Netzteil und Pumpe"),
          checklistItem("Schläuche & Wasserbehälter", "Zum Anschließen an die Pumpe"),
          checklistItem("Verbindungskabel", "Jumperkabel für die Verbindungen zwischen Arduino, Sensor und Relais"),
          checklistItem("USB-Kabel", "Zum Übertragen des Codes auf den Arduino und zur Stromversorgung")
        )
      ),

      div(
        className := "task-box",
        div(
          className := "safety-warning",
          backgroundColor := "#ffe5e5",
          border := "3px solid #c62828",
          padding := "14px",
          marginBottom := "14px",
          h3("⚠️ WICHTIGE SICHERHEITSREGEL"),
          p(
            strong("Niemals Strom ohne Aufseher einschalten! "),
            "Die Stromversorgung darf nur zusammen mit einer betreuenden Person aktiviert werden."
          )
        )
      ),

      if (showWiringDetails)
        div(
          className := "task-box",
          h3("🔧 Aufbau & Verkabelung"),
          p("So werden die Komponenten verbunden:"),
          div(
            className := "wiring-diagram",
            h4("Feuchtigkeitssensor:"),
            ul(
              li("+ → Arduino D2"),
              li("- → Arduino GND"),
              li("S (Signal) → Arduino A0 (analoger Eingang)")
            ),
            div(
              className := "info-box small-info",
              strong("Hinweis: "),
              "Wir schließen den Sensor an einen digitalen Pin (D2) für die Stromversorgung an, ",
              "damit wir ihn nur bei Bedarf einschalten können und so die Lebensdauer verlängern."
            ),
            h4("Relais-Modul:"),
            ul(
              li("DC+ → Arduino 5V"),
              li("DC- → Arduino GND"),
              li("IN → Arduino D8 (digitaler Eingang)")
            ),
            h4("Pumpe:"),
            ul(
              li("Pumpe + → Relais NO (Normally Open)"),
              li("Pumpe - → Netzteil -"),
              li("Netzteil + → Relais COM (Common)")
            ),
            div(
              className := "info-box small-info",
              strong("Wichtig: "),
              "Das Relais trennt die Pumpe vom Arduino-Stromkreis. ",
              "Niemals die Pumpe direkt am Arduino anschließen! ",
              "Zwischen Pumpe, Relais und Netzteil keine Jumperkabel verwenden!"
            ),
            p(
              "Das Arduino kann nur kleine Ströme direkt schalten, aber die Pumpe braucht mehr Strom. ",
              "Der Arduino steuert das Relais und das Relais schaltet die Pumpe an/aus."
            )
          )
        )
      else
        div(
          className := "task-box",
          h3("Aufbauskizze / Schaltplan"),
          p("Schaut euch die Aufbauschritte an und klickt durch die Bilder:"),
          div(
            className := "wiring-diagram",
            div(
              display := "flex",
              flexDirection := "column",
              gap := "10px",
              alignItems := "center",
              div(
                display := "flex",
                gap := "10px",
                button(
                  "← Zurück",
                  className := "btn-nav",
                  onClick --> { _ =>
                    currentBuildStep.update(i => (i - 1 + buildSteps.length) % buildSteps.length)
                  }
                ),
                span(
                  fontWeight := "600",
                  child.text <-- currentBuildStep.signal.map(i => s"Schritt ${i + 1} / ${buildSteps.length}")
                ),
                button(
                  "Weiter →",
                  className := "btn-nav",
                  onClick --> { _ =>
                    currentBuildStep.update(i => (i + 1) % buildSteps.length)
                  }
                )
              ),
              img(
                src <-- currentBuildStep.signal.map(i => buildSteps(i)),
                alt <-- currentBuildStep.signal.map(i => s"Aufbau Schritt ${i + 1}"),
                maxWidth := "100%",
                width := "820px",
                border := "2px solid #d5dbe3",
                borderRadius := "8px"
              ),
              child <-- currentBuildStep.signal.map {
                case 0 =>
                  div(
                    width := "820px",
                    div(
                      className := "info-box",
                      strong("Hinweis:"),
                      p(
                        "In den nächsten Schritten benötigt ihr Jumperkabel. ",
                        "Nehmt euch jetzt schon ein paar Jumperkabel und legt sie beiseite."
                      )
                    )
                  )
                case 1 =>
                  div(
                    width := "820px",
                    div(
                      className := "info-box",
                      strong("Hinweis:"),
                      p(
                        "Nehmt euch jetzt einen Arduino Nano, ein Relais und den Feuchtigkeitssensor. ",
                        "Diese Bauteile verbinden wir gleich miteinander."
                      )
                    )
                  )
                case 2 =>
                  div(
                    display := "flex",
                    gap := "12px",
                    width := "820px",
                    div(
                      className := "info-box",
                      flex := "1",
                      strong("Verkabelung:"),
                      ul(
                        li(" Sensor + __ Arduino D2"),
                        li(" Sensor - __ Arduino GND"),
                        li(" Sensor S __ Arduino A0")
                      )
                    ),
                    div(
                      className := "info-box",
                      flex := "2",
                      strong("Was macht diese Verkabelung?"),
                      p(
                        "Der Pin D2 versorgt den Sensor mit Strom, wenn wir Werte messen wollen. ",
                        "Er wird nicht permanent mit Strom versorgt, damit der Sensor länger hält. ",
                      ),
                      p(
                        "GND ist der Minuspol (Masse) der Schaltung. ",
                        "Ohne die Verbindung zu GND ist der Stromkreis nicht geschlossen und der Sensor kann keine stabilen Werte liefern."
                      ),
                      p(
                        "Über A0 liest der Arduino den analogen Messwert ein. ",
                        "Aus diesem Wert entscheidet euer Programm später, ob die Pumpe laufen soll oder nicht."
                      )
                    )
                  )
                case 3 =>
                  div(
                    display := "flex",
                    gap := "12px",
                    width := "820px",
                    div(
                      className := "info-box",
                      flex := "1",
                      strong("Verkabelung:"),
                      ul(
                        li(" Relais DC+ __ Arduino 5V"),
                        li(" Relais DC- __ Arduino GND"),
                        li(" Relais IN __ Arduino D8")
                      )
                    ),
                    div(
                      className := "info-box",
                      flex := "2",
                      strong("Was macht diese Verkabelung?"),
                      p(
                        "Das Relais wird mit 5V und GND vom Arduino versorgt. ",
                        "Über den Pin D8 steuert der Arduino das Relais an. "
                      ),
                      p(
                        "Das Relais funktioniert als Schalter zwischen Arduino und Pumpe. ",
                        "So kann der Arduino die Pumpe sicher ein- und ausschalten, obwohl die Pumpe mehr Strom braucht, als der Arduino liefern kann."
                      ),
                      p(
                        "Wichtig: Die Pumpe wird nicht direkt am Arduino angeschlossen, ",
                        "sondern über das Relais geschaltet."
                      )
                    )
                  )
                case 4 =>
                  div(
                    width := "820px",
                    div(
                      className := "info-box",
                      strong("Zwischenstand:"),
                      p(
                        "So sollte euer Aufbau bis hier aussehen. ",
                        "Vergleicht eure Verkabelung mit dem Bild und prüft kurz alle Verbindungen, bevor es weitergeht."
                      )
                    )
                  )
                case 5 =>
                  div(
                    width := "820px",
                    div(
                      className := "info-box",
                      strong("Hinweis:"),
                      p(
                        "Schneidet euch jetzt 3 Stücke Draht zurecht und legt sie bereit. ",
                        "Diese braucht ihr in den nächsten Schritten. Jumperkabel werden ab jetzt nicht mehr verwendet."
                      )
                    )
                  )
                case 6 =>
                  div(
                    width := "820px",
                    div(
                      className := "info-box",
                      strong("Hinweis:"),
                      p(
                        "Nehmt euch jetzt das Relais, die Pumpe und den Adapter. ",
                        "Den Adapter verbinden wir später mit dem Netzteil."
                      ),
                      p(
                        "Das Relais ist dasselbe wie vorher, ",
                        "aber wir arbeiten jetzt mit den Anschlüssen auf der anderen Seite."
                      )
                    )
                  )
                case 7 =>
                  div(
                    display := "flex",
                    gap := "12px",
                    width := "820px",
                    div(
                      className := "info-box",
                      flex := "1",
                      strong("Verkabelung:"),
                      ul(
                        li(" Pumpe + __ Relais NO (Normally Open)"),
                        li(" Pumpe - __ Adapter -"),
                        li(" Adapter + __ Relais COM (Common)")
                      )
                    ),
                    div(
                      className := "info-box",
                      flex := "2",
                      strong("Was macht diese Verkabelung?"),
                      p(
                        "Hier verbindet ihr Relais, Pumpe und Adapter mit den vorbereiteten Drähten. ",
                        "Das Relais schaltet später den Strom zur Pumpe."
                      ),
                      p(
                        "Der Pluspol vom Adapter geht über COM und NO zur Pumpe, ",
                        "der Minuspol vom Adapter wird direkt mit der Pumpe verbunden."
                      ),
                      p(
                        "Wichtig: Für diese Verbindung keine Jumperkabel verwenden, ",
                        "sondern die zugeschnittenen Drähte."
                      )
                    )
                  )
                case 8 =>
                  div(
                    width := "820px",
                    div(
                      className := "info-box",
                      strong("Hinweis:"),
                      p(
                        "Verbindet jetzt den Adapter mit dem Netzteil. ",
                        "Achtet dabei darauf, dass Plus und Minus richtig angeschlossen sind."
                      )
                    )
                  )
                case 9 =>
                  div(
                    width := "820px",
                    div(
                      className := "info-box",
                      strong("Hinweis:"),
                      p(
                        "Nehmt von der großen PVC-Schlauchrolle zwei Stücke mit jeweils 50 cm Länge. ",
                        "Diese beiden Schlauchstücke werden gleich für die Pumpe benötigt."
                      )
                    )
                  )
                case 10 =>
                  div(
                    width := "820px",
                    div(
                      className := "info-box",
                      strong("Hinweis:"),
                      p(
                        "Verbindet jetzt die beiden Schlauchstücke mit der Pumpe. ",
                        "Ein Schlauchstück kommt an den Eingang und das andere an den Ausgang der Pumpe."
                      )
                    )
                  )
                case _ => emptyNode
              }
            )
          )
        ),

      emptyNode
    )
  }

  def checklistItem(name: String, description: String): HtmlElement = {
    label(
      className := "checklist-item",
      input(
        typ := "checkbox",
        controlled(
          checked <-- checkedItems.signal.map(_.contains(name)),
          onInput.mapToChecked --> { isChecked =>
            checkedItems.update { existing =>
              if (isChecked) existing + name else existing - name
            }
          }
        )
      ),
      div(
        className := "checklist-content",
        strong(name),
        br(),
        span(className := "description", description)
      )
    )
  }
}
