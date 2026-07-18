package it.evadid.homepage.workbook.legacy.plantworkshop

import com.raquo.laminar.api.L.*
import it.evadid.homepage.workbook.legacy.plantworkshop.tasks.{Task0_Motivation, Task1_ComponentChecklist, Task2_MoistureSensor, Task3_PumpControl, Task4_Combined, Task5_Test}
import org.scalajs.dom

/** App shell that assembles all tasks. */
object PlantWorkshopApp {
  val currentTask: Var[Int] = Var(0)
  val isAdvancedMode: Var[Boolean] = Var(false)

  private var beforeUnloadInstalled: Boolean = false

  private def installReloadConfirmation(): Unit = {
    if (beforeUnloadInstalled) return
    beforeUnloadInstalled = true

    dom.window.addEventListener(
      "beforeunload",
      (e: dom.BeforeUnloadEvent) => {
        e.preventDefault()
        e.returnValue = ""
      }
    )
  }

  def render(container: dom.Element): Unit = {
    installReloadConfirmation()
    com.raquo.laminar.api.L.render(container, appElement)
  }

  def appElement: HtmlElement = {
    div(
      className := "container",
      navigationBar(),
      div(
        className := "task-content",
        child <-- currentTask.signal.map {
          case 0 => Task0_Motivation.render()
          case 1 => Task1_ComponentChecklist.render()
          case 2 => Task3_PumpControl.render(isAdvancedMode.signal)
          case 3 => Task2_MoistureSensor.render(isAdvancedMode.signal)
          case 4 => Task4_Combined.render(isAdvancedMode.signal)
          case 5 => Task5_Test.render()
          case _ => div("Unbekannte Aufgabe")
        }
      ),
      navigationButtons()
    )
  }

  def navigationBar(): HtmlElement = {
    val taskTitles = List(
      "0. Motivation",
      "1. Bauteile & Aufbau",
      "2. Pumpe steuern",
      "3. Feuchtigkeit messen",
      "4. Messwerte & Pumpe",
      "5. Test & Fertig"
    )

    div(
      className := "navigation-bar",
      h1("Girls Day - Automatische Pflanzen-Bewässerung"),
      div(
        className := "progress-steps",
        taskTitles.zipWithIndex.map { case (title, idx) =>
          div(
            className <-- currentTask.signal.map { current =>
              val base = "progress-step"
              val active = if (current == idx) " active" else ""
              base + active
            },
            onClick --> { _ => currentTask.set(idx) },
            title
          )
        }
      )
    )
  }

  def navigationButtons(): HtmlElement = {
    div(
      className := "navigation-buttons",
      button(
        "← Zurück",
        className := "btn-nav",
        disabled <-- currentTask.signal.map(_ == 0),
        onClick --> { _ => currentTask.update(t => Math.max(0, t - 1)) }
      ),
      button(
        "Fertig →",
        className := "btn-nav btn-primary",
        disabled <-- currentTask.signal.map(_ == 5),
        onClick --> { _ =>
          currentTask.update(t => Math.min(5, t + 1))
          dom.window.scrollTo(0, 0)
        }
      )
    )
  }
}
