package interactionPlugins.blockEnvironment.feedback

import it.evadid.core.datastructures.language.AppLanguage
/**
 * Encapsulates the collection of tests, fixtures, packages and hints that
 * should run for a specific submission.
 */
final case class BlockFeedbackTestPlan(
  visibleTests: Seq[BlockFeedbackPythonTest],
  hiddenTests: Seq[BlockFeedbackPythonTest],
  fixtures: Seq[BlockFeedbackPythonFixture],
  packages: Seq[String],
  timeoutMs: Int,
  derivedHints: Seq[String]
)

/**
 * Factory that derives a concrete test plan from the submission metadata.
 * This is the place to introduce generative or heuristics-driven test selection
 * and hint generation.
 */
object BlockFeedbackTestFactory:

  def deriveTestPlan(request: BlockFeedbackRequest): BlockFeedbackTestPlan =
    val config = request.config
    val hints = deriveHintsFromSubmission(request)

    BlockFeedbackTestPlan(
      visibleTests = config.visibleTests,
      hiddenTests = config.hiddenTests,
      fixtures = config.fixtures,
      packages = config.packages,
      timeoutMs = config.timeoutMs,
      derivedHints = hints
    )

  private def deriveHintsFromSubmission(
    request: BlockFeedbackRequest
  ): Seq[String] =
    val code = request.pythonSource.trim
    val builder = Seq.newBuilder[String]
    if code.isEmpty then
      if request.humanLanguage == AppLanguage.German then
        builder += "Noch kein Code vorhanden. Bitte starte mit einer Lösungsskizze."
      else
        builder += "No code yet. Please start with a solution sketch."
    if code.contains("TODO") then
      builder += "TODO-Marker sind noch vorhanden. Überarbeite den Code vor dem Einreichen."
    if request.submissionNr > 3 then
      builder += "Du hast mehrfach eingereicht. Schau dir die letzten Änderungen kritisch an."
    builder.result()
