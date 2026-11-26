package it.evadid.workbook.elements.interactionElements.programming.programmingExerciseRegular

import it.evadid.vm.conversion.dependencies.{ProgrammingEnvironment, ProgrammingLibrary}
import it.evadid.workbook.elements.interactionElements.programming.{ProgrammingExerciseEnvironment, ProgrammingTestGrader}
import it.evadid.core.datastructures.language.AppLanguage.{Java, ProgrammingLanguage, Python}
import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.vm.simulation.data.DataWorld
import it.evadid.workbook.abstractions.InteractionFormat.SortListExerciseFormat
import it.evadid.workbook.abstractions.grading.WorkbookInteractionGrader
import it.evadid.workbook.abstractions.{WorkbookElement, WorkbookInteractionElement}
import it.evadid.workbook.elements.interactionElements.programming.AdditionalProgrammingTestInfo
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.ProgramStructureGrader
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState
import it.evadid.workbook.jsonFactory.WorkbookElementFactory.SimpleWorkbookElementFactory
import it.evadid.workbook.jsonFactory.{WorkbookElementFactory, WorkbookElementSerializable}
import upickle.default.*

/** How a sorting solution must work, beyond producing the sorted list. */
enum SortingMethod derives ReadWriter {
  /** Any operations, as long as the list ends sorted. */
  case AnyMethod
  /** Values only move by `NumberList.swap`, never by `set`. */
  case SwapsOnly
  /** Only neighbouring swaps, exactly one per inversion (bubble or insertion sort by swapping). */
  case BubbleSort
}

/**
 * Sorting counterpart of `TurtleRecreateShapeInteraction`: the program defines `sortFunction`, which sorts
 * the provided `NumberList` ascending (in Snap: a block of that name using the number-list blocks). One test per entry of `testLists` calls it on that list; the editor
 * and overview replay its list operations step by step on `exampleList`.
 */
case class SortListExercise(
    override val elementId: String,
    initProgram: ProgrammingState,
    sortFunction: String,
    exampleList: Seq[Int] = SortListExercise.defaultExample,
    testLists: Seq[Seq[Int]] = SortListExercise.defaultTestLists,
    method: SortingMethod = SortingMethod.AnyMethod,
    allowedLanguages: Seq[ProgrammingLanguage] = SortListExercise.defaultLanguages,
    structureTests: Seq[ProgramStructureGrader] = Seq.empty,
    instructionText: Option[LanguageMapContentId] = None,
    environment: ProgrammingEnvironment = SortListExercise.defaultEnvironment
) extends WorkbookInteractionElement[SortListExerciseFormat.type] with ProgrammingExerciseEnvironment {
  require(sortFunction.nonEmpty && sortFunction == sortFunction.trim, "A sorting exercise needs the name of its sort function.")
  require(testLists.nonEmpty, "A sorting exercise needs at least one test list.")
  require(allowedLanguages.nonEmpty, "A sorting exercise needs at least one language.")

  override val defaultValue: ProgrammingState = initProgram
  override lazy val childrenOfThisElement: Seq[WorkbookElement] = List()
  override val associatedFactory: WorkbookElementFactory[SortListExercise] = SortListExercise.factory

  def exampleWorld: DataWorld = DataWorld(list = exampleList.toVector)

  /** The checks every sort test applies besides the sorted result. */
  def methodExpectations(list: Seq[Int]): Seq[DataExpectation] = method match {
    case SortingMethod.AnyMethod => Nil
    case SortingMethod.SwapsOnly => Seq(DataExpectation.AvoidsListOperation(ListOperation.Set))
    case SortingMethod.BubbleSort => Seq(DataExpectation.OnlyAdjacentSwaps, DataExpectation.SwapCount(ProgramDataTestGrader.inversions(list)))
  }

  val sortTests: Seq[ProgramDataTestGrader] = testLists.zipWithIndex.map { (list, index) =>
    ProgramDataTestGrader(s"$elementId-sort-$index", LanguageMapContentId("basic/sortTestList"), sortFunction, Nil,
      DataWorld(list = list.toVector), DataExpectation.ListEquals(list.sorted) +: methodExpectations(list),
      publicDescriptionArguments = Map("list" -> list.mkString("[", ", ", "]")))
  }

  override val gradingElements: Seq[WorkbookInteractionGrader[ProgrammingState, AdditionalProgrammingTestInfo]] =
    ProgrammingTestGrader.inEnvironment(sortTests ++ structureTests, environment)
}

object SortListExercise {
  val defaultExample: Seq[Int] = Seq(5, 1, 4, 2, 8)
  /** Typical and edge cases: unsorted, reversed, already sorted, duplicates, one value, empty. */
  val defaultTestLists: Seq[Seq[Int]] = Seq(Seq(5, 1, 4, 2, 8), Seq(6, 5, 4, 3, 2, 1), Seq(1, 2, 3, 4), Seq(3, 1, 3, 2, 1), Seq(42), Seq())
  val defaultLanguages: Seq[ProgrammingLanguage] = Seq(Python, Java)
  /** The number list and console output. */
  val defaultEnvironment: ProgrammingEnvironment = ProgrammingEnvironment.of(ProgrammingLibrary.NumberList, ProgrammingLibrary.System)

  val factory: WorkbookElementFactory[SortListExercise] = new SimpleWorkbookElementFactory[SortListExercise]() {
    override protected val constructorFieldOrder = List("elementId", "initProgram", "sortFunction", "exampleList", "testLists",
      "method", "allowedLanguages", "structureTests", "instructionText", "environment")

    override def finishSerialization(baseElement: WorkbookElementSerializable, element: SortListExercise): WorkbookElementSerializable =
      val serialized = baseElement
        .withElementAddedAs("initProgram", element.initProgram)
        .withElementAdded("sortFunction", element.sortFunction)
        .withElementAddedAs("exampleList", element.exampleList)
        .withElementAddedAs("testLists", element.testLists)
        .withElementAddedAs("method", element.method)
        .withElementAddedAs("allowedLanguages", element.allowedLanguages)
        .withElementAddedAs("structureTests", element.structureTests)
        .withElementAddedAs("instructionText", element.instructionText)
      if element.environment == defaultEnvironment then serialized else serialized.withElementAddedAs("environment", element.environment)

    override def finishDeserialization(serialized: WorkbookElementSerializable): SortListExercise =
      SortListExercise(
        serialized.elementId,
        serialized.getElementAs[ProgrammingState]("initProgram"),
        serialized.getElementAs[String]("sortFunction"),
        serialized.getElementAs[Seq[Int]]("exampleList"),
        serialized.getElementAs[Seq[Seq[Int]]]("testLists"),
        serialized.getElementAs[SortingMethod]("method"),
        serialized.getElementAs[Seq[ProgrammingLanguage]]("allowedLanguages"),
        serialized.getElementAs[Seq[ProgramStructureGrader]]("structureTests"),
        serialized.getElementAs[Option[LanguageMapContentId]]("instructionText"),
        serialized.getOptionalElementAs[ProgrammingEnvironment]("environment", defaultEnvironment))
  }
}
