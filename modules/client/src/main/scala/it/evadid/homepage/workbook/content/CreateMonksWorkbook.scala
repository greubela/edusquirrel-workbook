package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.file.CopyrightInfo
import it.evadid.core.datastructures.geometry.{Line, Point}
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.abstractions.{TypeOfTextDisplay, WorkbookDisplayElement, WorkbookElement}
import it.evadid.workbook.elements.displayElements.WorkbookImageElement.LanguageMapBasedWorkbookImageElement
import it.evadid.workbook.elements.displayElements.LabeledWorkbookElement.HintLabel
import it.evadid.workbook.elements.interactionElements.choice.ChoiceInteraction
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleGraphic.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleRecreateShapeInteraction
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingStatePythonString
import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingEditorPalette
import it.evadid.workbook.elements.interactionElements.reorderExercise.ReorderInteraction.ReorderMapIdInteraction
import it.evadid.workbook.elements.interactionElements.slideshow.*
import it.evadid.workbook.elements.interactionElements.table.{AnswerTableInteraction, EditableTableCell}
import it.evadid.workbook.elements.structureElements.{ExerciseGroup, Workbook}

/** Recursion workbook "Die Mönche von Mons Komputarius".
 *
 * Parts: 0 (editor basics), A (monks), B (outward/return path), C (tower), D (bridge to the final task)
 * and the Koch curve practice (section 3).
 *
 * Programming tasks of parts 0 and C/D are shown as placeholders until the Java turtle runner supports
 * `Turtle.turnLeft`, `Turtle.penUp` and `Turtle.penDown`. Element IDs are stable per task (`monks-a2`, ...).
 */
case class CreateMonksWorkbook(override val fullInfo: FullInfo) extends WorkbookFactory {

  import CreateMonksWorkbook.*

  override val workbookId: String = "MonksWorkbook"

  override def availableLanguages: List[AppLanguage.HumanLanguage] = List(AppLanguage.German, AppLanguage.English)

  private def key(name: String): String = "monksworkbook/" + name

  private def lm(name: String): LanguageMapContentId = LanguageMapContentId(key(name))

  private def panel(scene: Scene): WorkbookDisplayElement =
    ExerciseGroup("monks-panel-" + scene.id, List(
      LanguageMapBasedWorkbookImageElement(
        "monks-image-" + scene.id,
        LanguageMapContentId(key(scene.imageKey)),
        TypeOfTextDisplay.URL_RELATIVE_TO_WORKBOOK_RESOURCES,
        Some(LanguageMapContentId(key(scene.dialogueKey))),
        Some(CopyrightInfo.fromAi)
      ),
      instructionLabeledPair(key("dialoguelabel"), key(scene.dialogueKey), HintLabel)
    ))

  // ---- task builders: the task `id` determines the container title and all language-map keys

  private def task(id: String, elements: List[WorkbookElement]): WorkbookElement =
    container(key(id + "title"), elements)

  private def text(name: String): WorkbookElement = instructionMarkdown(key(name))

  private def prompt(id: String): WorkbookElement = text(id + "prompt")

  /** Options are `<id>-opt0..n-1` unless explicit option keys are given. */
  private def choice(id: String, expected: List[Int], optionCount: Int, multiple: Boolean = false,
                     optionKeys: Option[List[String]] = None): ChoiceInteraction =
    ChoiceInteraction("monks-" + id, lm(id + "-question"),
      optionKeys.getOrElse(List.tabulate(optionCount)(i => s"$id-opt$i")).map(lm), multiple, Some(expected))

  /** One single-choice row per statement; `answers` name the correct category for each `<id>-row<i>`. */
  private def assign(id: String, categories: List[String], answers: List[String]): List[WorkbookElement] =
    answers.zipWithIndex.map { (answer, i) =>
      ChoiceInteraction(s"monks-$id-$i", lm(s"$id-row$i"), categories.map(c => lm("cat-" + c)),
        expected = Some(List(categories.indexOf(answer))))
    }

  /** Free-input table. A cell lists accepted spellings separated by `|`; `columns` are full language-map keys. */
  private def table(id: String, columns: List[String], answers: List[List[String]]): AnswerTableInteraction =
    AnswerTableInteraction("monks-" + id, lm(id + "-caption"),
      answers.indices.map(i => lm(s"$id-row$i")).toList, columns.map(lm),
      answers.map(_.map(cell => EditableTableCell(expected = Some(cell.split('|').toList)))))

  private def singleColumn(id: String, answers: List[String]): AnswerTableInteraction =
    table(id, List("answercolumn"), answers.map(List(_)))

  /** `items` are listed in their correct order; the interaction shuffles them. */
  private def reorder(id: String, itemCount: Int): ReorderMapIdInteraction =
    ReorderMapIdInteraction("monks-" + id, List.tabulate(itemCount)(i => lm(s"$id-item$i")), seed = id.hashCode.toLong)

  /** Placeholder for an editor task: prompt, starting code and a note that the editor follows. */
  private def editorTask(id: String, extra: List[WorkbookElement] = Nil): WorkbookElement =
    task(id, List(prompt(id), text(id + "code"),
      instructionLabeledPair(key("placeholderlabel"), key("placeholdertext"), HintLabel)) ++ extra)

  // Building once makes IDs and state references stable across createWorkbook/createEverything calls.
  override lazy val createWorkbook: Workbook = workbook(
    key("workbooktitle"),
    List(
      section("monks-editor", key("partzero"), List(
        container(key("partzero"), List(text("zerointro"))),
        editorTask("e1"), editorTask("e2"), editorTask("e3"))),

      section("monks-story", key("parta"), List(
        container(key("parta"), List(
          text("introduction"),
          Slideshow("monks-theater", theaterScenes.map(panel)),
          text("schemaintro"), text("schemacode"))),
        task("a1", List(prompt("a1"), reorder("a1", 8))),
        task("a2", List(singleColumn("a2", List("3", "5", "5 4 2|5,4,2|5, 4, 2|5 , 4 , 2", "5")))),
        task("a3", List(choice("a3", List(1), 4))),
        task("a4", List(prompt("a4")) ++ assign("a4",
          List("abort", "simplify", "call", "combine", "first", "none"),
          List("simplify", "call", "abort", "combine", "first", "none"))),
        task("a5", List(prompt("a5"), text("a5code")) ++ assign("a5",
          List("abort", "simplify", "call", "combine"),
          List("abort", "simplify", "simplify", "call", "combine"))),
        task("a6", List(choice("a6", List(1, 3, 5), 6, multiple = true))),
        task("a7", List(choice("a7", List(1, 3, 5), 6, multiple = true), text("a7note"))),
        task("a8", List(text("a8intro"),
          choice("a8a", List(1), 4),
          singleColumn("a8b", List("6")),
          choice("a8c", List(2), 4))))),

      section("monks-phases", key("partb"), List(
        task("b1", List(prompt("b1"), text("b1code"), reorder("b1", 8))),
        task("b2", List(choice("b2", List(1, 3, 4, 5), 6, multiple = true))),
        task("b3", List(prompt("b3"), text("b3code")) ++ assign("b3",
          List("exception", "overflow", "ascending", "correct"),
          List("exception", "overflow", "ascending", "correct"))))),

      section("monks-tower", key("partc"), List(
        container(key("partc"), List(
          prompt("c0"),
          Slideshow("monks-counting-story", countingScenes.map(panel)),
          text("countingend"))),
        task("c1", List(prompt("c1")) ++ assign("c1",
          List("abort", "simplify", "call", "combine", "state"),
          List("simplify", "state", "call", "abort", "combine"))),
        task("c2", List(
          table("c2", List("c2-col0", "c2-col1", "c2-col2"), List(List("2", "1", "1"), List("1", "2", "2"), List("0", "3", "3"))),
          singleColumn("c2b", List("4")),
          text("c2c-prompt"), reorder("c2c", 3))),
        task("c3", List(prompt("c3"), text("c3code")) ++ assign("c3",
          List("variant-a", "variant-b", "variant-c"), List("variant-a", "variant-b", "variant-c"))),
        task("c4", List(singleColumn("c4", List("6", "3", "5", "3")))),
        task("c5", List(text("functionalexplanation"), choice("c5", List(1, 3, 4, 5), 6, multiple = true))),
        task("c6", List(choice("c6", List(2), 3, multiple = true,
          optionKeys = Some(List("cat-variant-a", "cat-variant-b", "cat-variant-c"))))),
        container(key("helperslabel"), List(instructionCollapsibleHint(key("helperslabel"), key("helperscode")))),
        editorTask("e4"), editorTask("e5"), editorTask("e6a"), editorTask("e6b"))),

      section("monks-bridge", key("partd"), List(
        task("d1", List(choice("d1a", List(1), 4), choice("d1b", List(1, 3), 5, multiple = true), text("d1note"))),
        task("d2", List(text("d2code"), table("d2", List("answercolumn"), List(List("15"), List("8"), List("9"))))),
        editorTask("e7", List(singleColumn("e7b", List("15")))),
        editorTask("e8", List(singleColumn("e8b", List("7")))))),

      // Section 3 (Koch curve) is authored separately and intentionally unchanged.
      section("monks-koch", key("kochtitle"),
        List(container(key("kochintroductioncont"), List(
          instructionMarkdown(key("kochintroduction"))
        ))) ++ (0 to 2).toList.map { depth =>
          container(key("koch" + depth + "title"), List(
            instructionMarkdown(key("koch" + depth)),
            TurtleRecreateShapeInteraction(
              "monks-koch-" + depth,
              ProgrammingStatePythonString(""),
              kochTarget(depth),
              ProgrammingEditorPalette.PythonCompatibleSnap,
              Map.empty[String, Integer])))
        })
    ),
    User.AndreGreubel
  )
}

object CreateMonksWorkbook {
  /** One speaking turn or one visual action/state per frame. Empty speaker
   * means silence (the dialogue is exactly "..."). Action labels document
   * the sequence; they are never displayed as stage directions.
   */
  case class Scene(id: String, imageKey: String, dialogueKey: String = "silence",
                   speaker: Option[String] = None, action: Option[String] = None) derives upickle.default.ReadWriter {
    require(speaker.isEmpty || action.isEmpty, "A speaking frame cannot also perform an action")
    require(speaker.nonEmpty || dialogueKey == "silence", "Silent frames use only an ellipsis")
  }

  val theaterScenes: List[Scene] = List(
    Scene("arrival", "fileimage1", action = Some("travel")),
    Scene("entrance", "fileimage2", dialogueKey = "entrancedialogue", speaker = Some("R")),
    Scene("master-wait", "filebeattemplepause"),
    Scene("warning", "filebeatmasterwarning", dialogueKey = "warningdialogue", speaker = Some("M")),
    Scene("shout", "filebeattravelershout", dialogueKey = "shoutdialogue", speaker = Some("R")),
    Scene("shout-pause", "filebeattemplepause"),
    Scene("cards-demand", "fileimage3", dialogueKey = "cards-demanddialogue", speaker = Some("R")),
    Scene("help-wait", "filebeattemplepause"),
    Scene("in-deed-one", "filebeathelpmaster", dialogueKey = "in-deed-onedialogue", speaker = Some("M")),
    Scene("help-uncertain", "filebeathelpuncertain", dialogueKey = "help-uncertaindialogue", speaker = Some("R")),
    Scene("help-pause-one", "filebeattemplepause"),
    Scene("in-deed-two", "filebeathelpmaster", dialogueKey = "in-deed-onedialogue", speaker = Some("M")),
    Scene("help-question", "filebeathelpquestion", dialogueKey = "help-questiondialogue", speaker = Some("R")),
    Scene("help-pause-two", "filebeattemplepause"),
    Scene("in-deed-three", "filebeathelpmaster", dialogueKey = "in-deed-onedialogue", speaker = Some("M")),
    Scene("help-long-pause", "filebeattemplepause"),
    Scene("help-insistent", "filebeathelpinsistent", dialogueKey = "help-insistentdialogue", speaker = Some("R")),
    Scene("journey-question", "filebeatjourneyquestion", dialogueKey = "journey-questiondialogue", speaker = Some("M")),
    Scene("journey-answer", "filebeatjourneyanswer", dialogueKey = "journey-answerdialogue", speaker = Some("R")),
    Scene("return-question", "filebeatreturnquestion", dialogueKey = "return-questiondialogue", speaker = Some("M")),
    Scene("return-answer", "filebeatreturnanswer", dialogueKey = "return-answerdialogue", speaker = Some("R")),
    Scene("patience", "filebeatpatiencespeaking", dialogueKey = "patiencedialogue", speaker = Some("M")),
    Scene("tray-offered", "filebeattrayoffered", action = Some("offer-tray")),
    Scene("tray-instruction", "filebeattrayinstruction", dialogueKey = "tray-instructiondialogue", speaker = Some("M")),
    Scene("cards-deposited", "filebeatcardsdeposited", action = Some("deposit-cards")),
    Scene("tray-to-master", "filebeattraytomaster", action = Some("carry-tray")),
    Scene("watch", "filebeatwatchinstruction", dialogueKey = "watchdialogue", speaker = Some("M")),
    Scene("select-9-before", "filebeatselect9before", action = Some("select-9-before")),
    Scene("select-9-lifting", "filebeatselect9lifting", action = Some("select-9-lifting")),
    Scene("select-9-held", "filebeatselect9held", action = Some("select-9-held")),
    Scene("select-7-before", "filebeatselect7before", action = Some("select-7-before")),
    Scene("select-7-lifting", "filebeatselect7lifting", action = Some("select-7-lifting")),
    Scene("select-7-held", "filebeatselect7held", action = Some("select-7-held")),
    Scene("select-4-before", "filebeatselect4before", action = Some("select-4-before")),
    Scene("select-4-lifting", "filebeatselect4lifting", action = Some("select-4-lifting")),
    Scene("select-4-held", "filebeatselect4held", action = Some("select-4-held")),
    Scene("select-2-before", "filebeatselect2before", action = Some("select-2-before")),
    Scene("select-2-lifting", "filebeatselect2lifting", action = Some("select-2-lifting")),
    Scene("select-2-held", "filebeatselect2held", action = Some("select-2-held")),
    Scene("waiting-monks", "filebeatwaitingmonks"),
    Scene("empty-delivery", "filebeatemptydelivery", action = Some("handoff-empty-tray")),
    Scene("empty-pause", "filebeatemptywait"),
    Scene("empty", "filebeatemptyspeaking", dialogueKey = "emptydialogue", speaker = Some("M")),
    Scene("giveback", "filebeatreturninstruction", dialogueKey = "givebackdialogue", speaker = Some("M")),
    Scene("empty-return", "filebeatemptyreturn", action = Some("return-empty-tray")),
    Scene("empty-carry", "filebeatemptycarry", action = Some("carry-empty-tray")),
    Scene("insert-2-right", "filebeatinsert2right", action = Some("insert-2-right")),
    Scene("insert-2-moving", "filebeatinsert2moving", action = Some("insert-2-moving")),
    Scene("insert-2-left", "filebeatinsert2left", action = Some("insert-2-left")),
    Scene("shift-4-before", "filebeatshift4before", action = Some("shift-4-before")),
    Scene("shift-4-moving", "filebeatshift4moving", action = Some("shift-4-moving")),
    Scene("insert-4-right", "filebeatinsert4right", action = Some("insert-4-right")),
    Scene("insert-4-moving", "filebeatinsert4moving", action = Some("insert-4-moving")),
    Scene("insert-4-left", "filebeatinsert4left", action = Some("insert-4-left")),
    Scene("shift-7-before", "filebeatshift7before", action = Some("shift-7-before")),
    Scene("shift-7-moving", "filebeatshift7moving", action = Some("shift-7-moving")),
    Scene("insert-7-right", "filebeatinsert7right", action = Some("insert-7-right")),
    Scene("insert-7-moving", "filebeatinsert7moving", action = Some("insert-7-moving")),
    Scene("insert-7-left", "filebeatinsert7left", action = Some("insert-7-left")),
    Scene("shift-9-before", "filebeatshift9before", action = Some("shift-9-before")),
    Scene("shift-9-moving", "filebeatshift9moving", action = Some("shift-9-moving")),
    Scene("insert-9-right", "filebeatinsert9right", action = Some("insert-9-right")),
    Scene("insert-9-moving", "filebeatinsert9moving", action = Some("insert-9-moving")),
    Scene("insert-9-left", "filebeatinsert9left", action = Some("insert-9-left")),
    Scene("sorted-delivery", "filebeatsorteddelivery", action = Some("handoff-sorted-tray")),
    Scene("sorted", "filebeatsortedspeaking", dialogueKey = "sorteddialogue", speaker = Some("M")),
    Scene("understood", "filebeatunderstoodspeaking", dialogueKey = "understooddialogue", speaker = Some("R")),
    Scene("farewell", "filebeatfarewellspeaking", dialogueKey = "farewelldialogue", speaker = Some("M")),
    Scene("farewell-walk", "fileimage13", action = Some("walk-away")))

  val countingScenes: List[Scene] = List(
    Scene("family", "filebeatfamilychildintro", dialogueKey = "familydialogue", speaker = Some("K")),
    Scene("family-father-reply", "filebeatfamilyfatherreply", dialogueKey = "family-father-replydialogue", speaker = Some("R")),
    Scene("family-child-question", "filebeatfamilychildquestion", dialogueKey = "family-child-questiondialogue", speaker = Some("K")),
    Scene("family-father-hurry", "filebeatfamilyfatherhurry", dialogueKey = "family-father-hurrydialogue", speaker = Some("R")),
    Scene("family-pause", "filebeatfamilypause"),
    Scene("count-explanation", "filebeatfamilysolution", dialogueKey = "count-explanationdialogue", speaker = Some("R")),
    Scene("count-paper", "filebeatcountpaper", action = Some("count-paper")),
    Scene("count-remove", "filebeatcountremove", action = Some("count-remove")),
    Scene("count-place", "filebeatcountplace", action = Some("count-place")),
    Scene("count-tally", "filebeatcounttally", action = Some("count-tally")),
    Scene("count-remove-yellow", "filebeatcountremoveyellow", action = Some("count-remove-yellow")),
    Scene("count-place-yellow", "filebeatcountplaceyellow", action = Some("count-place-yellow")),
    Scene("count-tally-two", "filebeatcounttallytwo", action = Some("count-tally-two")),
    Scene("count-remove-blue", "filebeatcountremoveblue", action = Some("count-remove-blue")),
    Scene("count-place-blue", "filebeatcountplaceblue", action = Some("count-place-blue")),
    Scene("count-tally-three", "filebeatcounttallythree", action = Some("count-tally-three")),
    Scene("count-empty", "filebeatcountempty"))

  /** Exact vector targets for the line and the first two Koch refinements.
   * Screen coordinates have y pointing down, so the triangular peak is above
   * the original horizontal line. Each subdivision preserves its endpoints.
   */
  def kochTarget(depth: Int): TurtleLineBasedProgram = {
    require(depth >= 0 && depth <= 2, "This practice uses Koch depths 0, 1 and 2")

    def segments(start: Point[Double], end: Point[Double], remaining: Int): List[Line[Double]] = {
      if (remaining == 0) List(Line(start, end))
      else {
        val dx = (end.x - start.x) / 3
        val dy = (end.y - start.y) / 3
        val first = Point(start.x + dx, start.y + dy)
        val last = Point(start.x + 2 * dx, start.y + 2 * dy)
        val peak = Point(first.x + dx / 2 + dy * math.sqrt(3) / 2,
          first.y + dy / 2 - dx * math.sqrt(3) / 2)
        List(start -> first, first -> peak, peak -> last, last -> end)
          .flatMap { case (from, to) => segments(from, to, remaining - 1) }
      }
    }

    TurtleLineBasedProgram(segments(Point(0.0, 0.0), Point(270.0, 0.0), depth))
  }
}
