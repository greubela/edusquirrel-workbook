package it.evadid.homepage.workbook.legacy.interactionPlugins.blockEnvironment.feedback.diagnosis

import scala.scalajs.js

/**
 * Minimal JSON encoding for `Diagnosis` to embed into prompts.
 *
 * Uses Scala.js `JSON.stringify` to avoid adding extra dependencies.
 */
object DiagnosisJson:

  private def safeString(value: String): String = Option(value).getOrElse("")

  def toJsonString(d: Diagnosis): String =
    val obj = js.Dynamic.literal(
      "category" -> d.category.toString,
      "primaryIssue" -> d.primaryIssue.toString,
      "secondaryIssues" -> d.secondaryIssues.map(_.toString).toJSArray,
      "severity" -> d.severity.toString,
      "confidence" -> d.confidence,
      "hypotheses" -> d.hypotheses.map { h =>
        js.Dynamic.literal(
          "issue" -> h.issue.toString,
          "confidence" -> h.confidence,
          "rationale" -> safeString(h.rationale)
        )
      }.toJSArray,
      "evidence" -> d.evidence.map { e =>
        js.Dynamic.literal(
          "id" -> e.id,
          "kind" -> e.kind.toString,
          "title" -> safeString(e.title),
          "details" -> safeString(e.details)
        )
      }.toJSArray,
      "taskProfile" -> js.Dynamic.literal(
        "tags" -> d.taskProfile.tags.toJSArray,
        "requiresPureFunction" -> d.taskProfile.requiresPureFunction,
        "ioLikelyForbidden" -> d.taskProfile.ioLikelyForbidden,
        "expectsReturnValue" -> d.taskProfile.expectsReturnValue,
        "outputFormatSensitive" -> d.taskProfile.outputFormatSensitive
      ),
      "recommendedNextChecks" -> d.recommendedNextChecks.toJSArray
    )

    js.JSON.stringify(obj, space = 2)

  extension [A](seq: Seq[A])
    private def toJSArray: js.Array[A] = js.Array(seq*)
