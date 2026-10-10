package it.evadid.homepage.workbook.content

import it.evadid.core.datastructures.file.CopyrightInfo
import it.evadid.core.datastructures.file.CopyrightInfo.AiGenerated
import it.evadid.core.datastructures.geometry.{Line, Point}
import it.evadid.core.datastructures.language.{AppLanguage, LanguageMapContentId}
import it.evadid.core.datastructures.user.User
import it.evadid.homepage.control.model.FullInfo
import it.evadid.workbook.abstractions.TypeOfTextDisplay.URL_TYPE
import it.evadid.workbook.abstractions.{TypeOfTextDisplay, WorkbookDisplayElement, WorkbookElement}
import it.evadid.workbook.elements.displayElements.WorkbookImageElement.LanguageMapBasedWorkbookImageElement
import it.evadid.workbook.elements.displayElements.LabeledWorkbookElement.HintLabel
import it.evadid.workbook.elements.interactionElements.basic.TextInteraction
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleGraphic.*
import it.evadid.workbook.elements.interactionElements.programming.programmingExerciseTurtle.TurtleRecreateShapeInteraction
import it.evadid.workbook.elements.interactionElements.programming.state.ProgrammingState.ProgrammingState.ProgrammingStatePythonString
import it.evadid.workbook.elements.interactionElements.programming.state.snap.ProgrammingEditorPalette
import it.evadid.workbook.elements.interactionElements.slideshow.*
import it.evadid.workbook.elements.structureElements.{ExerciseGroup, Workbook}

/** First digital edition of "Rekursion mit den Mönchen von Mons Komputarius" (29.04.2025).
 * Stable exercise IDs keep responses attached to their original PDF task numbers.
 */
case class CreateMonksWorkbook(override val fullInfo: FullInfo) extends WorkbookFactory {

  import CreateMonksWorkbook.*

  override val workbookId: String = "MonksWorkbook"

  override def availableLanguages: List[AppLanguage.HumanLanguage] = List(AppLanguage.German)

  private def key(name: String): String = "monksworkbook/" + name

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

  private def task(number: String): WorkbookElement =
    container(key("task" + number + "title"), List(
      instructionMarkdown(key("task" + number)),
      TextInteraction("monks-answer-" + number)))

  // Building once makes IDs and state references stable across createWorkbook/createEverything calls.
  override lazy val createWorkbook: Workbook = workbook(
    key("workbooktitle"),
    List(
      // story
      section("monks-story", key("storytitle"),
        List(
          container(key("task1atitle"), List(
            instructionMarkdown(key("introduction")),
            instructionMarkdown(key("task1a")),
            Slideshow("monks-theater", theaterScenes.map(panel)))
          ),
          container(key("task1btitle"),
            List(
              instructionMarkdown(key("task1b")),
              TextInteraction("monks-answer-1b"),
              instructionMarkdown(key("recursionexplanation"))
            ) ++ List(
              "1c", "1d", "1e", "1f", "1g"
            ).map(task),
          ),
        )),
      section("monks-recursion", key("sortingtitle"), List(
        container(key("task2atitle"),
          List(
            instructionMarkdown(key("task2a")),
            Slideshow("monks-counting-story", countingScenes.map(panel))
          ) ++
            List("2b", "2c", "2d", "2e", "2f", "2g", "2h").map(task) ++
            List(instructionMarkdown(key("functionalexplanation"))) ++ List("2i", "2j").map(task)
        ))),
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
