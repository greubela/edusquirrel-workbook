
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.{*, given}
import content.{CreateEmbroideryWorkbook, TestWorkbookFactory, plantworkshop}
import content.plantworkshop.PlantWorkshopApp
import org.scalajs.dom
import org.scalajs.dom.document
import workbook.htmlElements.container.HtmlFullScreenContainerElement
import workbook.model.info.AllWorkbookInfo

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}


private def info = AllWorkbookInfo.singleton
private def workbookInfoVar = AllWorkbookInfo.singleton.workbookInfoVar
private def fullscreenElement = AllWorkbookInfo.singleton.technicalElements.fullScreenContainer

private val idAndContentList: List[(String, Element)] = List(
  ("plantWorkshopApp", plantworkshop.PlantWorkshopApp.appElement),
  //("testEditor", HtmlFullscreenTurtleEditorElement(BeProgram.debugGraphicsProgram().fullProgram).getDomElement()),
  //("workbookTest", TestWorkbookFactory.createTestWorkbook(AllWorkbookInfo.singleton.technicalElements.fullScreenContainer).getDomElement()),
  //("worksheetMonks", TestWorkbook(fullscreenElement).getDomElement()),
  ("workbookEmbroidery", CreateEmbroideryWorkbook(info).createWorkbook.getDomElement()),
)

def insertWorkbookContent(): Unit = {

  def tryToLoad(containerId: String, contentElement: Element): Unit = {
    println("try to load: " + containerId)
    val container = document.getElementById(containerId)
    if (container != null) {
      println("Loading Content: " + containerId)
      val combinedElement = div(
        fullscreenElement.getDomElement(),
        contentElement
      )
      if (dom.document.readyState == "loading") {
        renderOnDomContentLoaded(container, combinedElement)
      } else {
        render(container, combinedElement)
      }
    }
  }

  idAndContentList.foreach { case (id, contentElement) => tryToLoad(id, contentElement) }

}

@main
def mainApp(): Unit = {

  //resetLocalStorage()

  insertWorkbookContent()
}

def resetLocalStorage(): Unit = {
  val map = (0 until dom.window.localStorage.length)
    .flatMap { i =>
      Option(dom.window.localStorage.key(i)).flatMap { key =>
        Option(dom.window.localStorage.getItem(key)).map(value => key -> value)
      }
    }
    .toMap

  println("[WARN] resetting local storage in MainApp!")

  map.keys.foreach(curKey => {
    println(curKey.toString + " -> " + map(curKey))
  })


  dom.window.localStorage.clear()
}


def insertWorkbook(): Unit = {

}

object Main:

  implicit val executionContext: ExecutionContextExecutor = ExecutionContext.global

end Main
/*
final case class SimpleExercise(
                                 id: String,
                                 englishTitle: String,
                                 duration: Double,
                                 instruction: String
                               ) extends ExerciseWithTitleDescription with ExerciseInstructionDescription {
  override def titleMap: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> englishTitle))

  override def estimatedTimeInMinutes: Double = duration

  def instructionMap: LanguageMap[HumanLanguage] = LanguageMap.mapBasedLanguageMap(Map(AppLanguage.English -> instruction))
}

final case class SampleSection(
                                override val title: String,
                                override val exercises: List[ExerciseWithTitleDescription],
                                override val sectionsRequiredBefore: List[ExerciseSection] = Nil,
                                override val sectionsRecommendedBefore: List[ExerciseSection] = Nil
                              ) extends ExerciseSection

def sampleSections: List[ExerciseSection] = {
  val introExercises = List(
    SimpleExercise("intro-1", "Getting Started", 3, "Read the welcome material."),
    SimpleExercise("intro-2", "First Steps", 5, "Complete the introductory quiz."),
    SimpleExercise("intro-3", "Warmup", 2, "Review the glossary.")
  )
  val basicsExercises = List(
    SimpleExercise("basic-1", "Core Concepts", 8, "Work through the foundational lesson."),
    SimpleExercise("basic-2", "Examples", 6, "Study the worked examples.")
  )
  val practiceExercises = List(
    SimpleExercise("practice-1", "Drills", 4, "Solve the practice problems."),
    SimpleExercise("practice-2", "Challenge", 9, "Attempt the challenge exercise."),
    SimpleExercise("practice-3", "Reflection", 3, "Write a short reflection.")
  )
  val extensionExercises = List(
    SimpleExercise("extension-1", "Project Setup", 7, "Prepare the project workspace."),
    SimpleExercise("extension-2", "Project Build", 10, "Implement the project milestone."),
    SimpleExercise("extension-3", "Review", 5, "Conduct a peer review.")
  )

  val sectionA = SampleSection("Orientation", introExercises)
  val sectionB = SampleSection("Fundamentals", basicsExercises, sectionsRequiredBefore = List(sectionA))
  val sectionC = SampleSection("Practice", practiceExercises, sectionsRequiredBefore = List(sectionB))
  val sectionD = SampleSection(
    "Project",
    extensionExercises,
    sectionsRequiredBefore = List(sectionB),
    sectionsRecommendedBefore = List(sectionA)
  )

  List(sectionA, sectionB, sectionC, sectionD)


}
*/