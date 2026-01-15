package interactionPlugins.blockEnvironment.feedback

import contentmanagement.model.language.AppLanguage

/**
 * In-memory registry of feedback exercise definitions.
 *
 * This keeps per-exercise feedback configuration close to the exercise id and
 * optionally includes the task statement translations.
 */
object BlockFeedbackExerciseRegistry {

  val addTwoNumbersExerciseId: String = "block:add-two-numbers"
  val maxInListExerciseId: String = "block:max-in-list"
  val balancedBracketsExerciseId: String = "block:balanced-brackets"
  val twoSumIndicesExerciseId: String = "block:two-sum-indices"

  private val english = AppLanguage.English
  private val german = AppLanguage.German

  val addTwoNumbers: FeedbackExerciseDefinition =
    FeedbackExerciseDefinition(
      id = addTwoNumbersExerciseId,
      titleTranslations = Map(
        english -> "Add two numbers",
        german -> "Zwei Zahlen addieren"
      ),
      statementTranslations = Map(
        english -> "Implement `add(a, b)` that returns the sum of two numbers.",
        german -> "Implementiere `add(a, b)`, das die Summe zweier Zahlen zurückgibt."
      ),
      config =
        BlockFeedbackConfig(
          enableVmStaticChecks = true,
          enablePythonStaticChecks = true,
          enableUnitTests = true,
          enableAiSummary = true,
          visibleTests = Seq(
            BlockFeedbackPythonTest(
              name = "add_small_positive",
              code = "assert add(1, 2) == 3",
              hint = Some("Teste zuerst einfache positive Zahlen.")
            ),
            BlockFeedbackPythonTest(
              name = "add_zero",
              code = "assert add(0, 5) == 5"
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "add_negative",
              code = "assert add(-3, 7) == 4",
              weight = 2.0,
              hint = Some("Achte auch auf negative Zahlen.")
            )
          ),
          fixtures = Nil,
          packages = Nil,
          timeoutMs = 4000
        )
    )

  val maxInList: FeedbackExerciseDefinition =
    FeedbackExerciseDefinition(
      id = maxInListExerciseId,
      titleTranslations = Map(
        english -> "Maximum in a list",
        german -> "Maximum einer Liste"
      ),
      statementTranslations = Map(
        english -> "Implement `max_in_list(xs)` that returns the maximum value in a list.",
        german -> "Implementiere `max_in_list(xs)`, das das Maximum einer Liste zurückgibt."
      ),
      config =
        BlockFeedbackConfig(
          enableVmStaticChecks = true,
          enablePythonStaticChecks = true,
          enableUnitTests = true,
          enableAiSummary = true,
          visibleTests = Seq(
            BlockFeedbackPythonTest(
              name = "simple_list",
              code = "assert max_in_list([1, 2, 3]) == 3"
            ),
            BlockFeedbackPythonTest(
              name = "unordered",
              code = "assert max_in_list([5, 1, 9, 2]) == 9"
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "with_negative",
              code = "assert max_in_list([-5, -1, -9]) == -1",
              weight = 2.0
            )
          ),
          fixtures = Nil,
          packages = Nil,
          timeoutMs = 4000
        )
    )

  val balancedBrackets: FeedbackExerciseDefinition =
    FeedbackExerciseDefinition(
      id = balancedBracketsExerciseId,
      titleTranslations = Map(
        english -> "Balanced brackets",
        german -> "Ausgeglichene Klammern"
      ),
      statementTranslations = Map(
        english -> "Implement `balanced_brackets(s)` that returns True iff all (), [], {} brackets in `s` are properly balanced and nested. Ignore all non-bracket characters.",
        german -> "Implementiere `balanced_brackets(s)`, das True zurückgibt genau dann, wenn alle (), [], {} Klammern in `s` korrekt ausgeglichen und verschachtelt sind. Ignoriere alle Nicht-Klammer-Zeichen."
      ),
      config =
        BlockFeedbackConfig(
          enableVmStaticChecks = true,
          enablePythonStaticChecks = true,
          enableUnitTests = true,
          enableAiSummary = true,
          visibleTests = Seq(
            BlockFeedbackPythonTest(
              name = "empty",
              code = "assert balanced_brackets(\"\") == True"
            ),
            BlockFeedbackPythonTest(
              name = "simple",
              code = "assert balanced_brackets(\"()[]{}\") == True"
            ),
            BlockFeedbackPythonTest(
              name = "nested_mixed",
              code = "assert balanced_brackets(\"{[()]}\") == True"
            ),
            BlockFeedbackPythonTest(
              name = "wrong_type",
              code = "assert balanced_brackets(\"(]\") == False",
              hint = Some("Achte auf passende Klammer-Typen und Reihenfolge.")
            ),
            BlockFeedbackPythonTest(
              name = "unclosed",
              code = "assert balanced_brackets(\"(()\") == False"
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "text_ignored",
              code = "assert balanced_brackets(\"a(b[c]{d}e)f\") == True",
              weight = 2.0
            ),
            BlockFeedbackPythonTest(
              name = "closing_first",
              code = "assert balanced_brackets(\")(\") == False",
              weight = 2.0
            ),
            BlockFeedbackPythonTest(
              name = "deep_nesting",
              code = "assert balanced_brackets(\"(((())))[]{}\") == True",
              weight = 2.0
            )
          ),
          fixtures = Nil,
          packages = Nil,
          timeoutMs = 4000
        )
    )

  val twoSumIndices: FeedbackExerciseDefinition =
    FeedbackExerciseDefinition(
      id = twoSumIndicesExerciseId,
      titleTranslations = Map(
        english -> "Two-sum indices",
        german -> "Zwei-Summe-Indizes"
      ),
      statementTranslations = Map(
        english -> "Implement `two_sum_indices(nums, target)` that returns a tuple `(i, j)` (0-based, with `i < j`) such that `nums[i] + nums[j] == target`. You may assume exactly one solution exists.",
        german -> "Implementiere `two_sum_indices(nums, target)`, das ein Tupel `(i, j)` (0-basiert, mit `i < j`) zurückgibt, sodass `nums[i] + nums[j] == target`. Du darfst annehmen, dass es genau eine Lösung gibt."
      ),
      config =
        BlockFeedbackConfig(
          enableVmStaticChecks = true,
          enablePythonStaticChecks = true,
          enableUnitTests = true,
          enableAiSummary = true,
          visibleTests = Seq(
            BlockFeedbackPythonTest(
              name = "classic",
              code = "assert two_sum_indices([2, 7, 11, 15], 9) == (0, 1)"
            ),
            BlockFeedbackPythonTest(
              name = "with_negative",
              code = "assert two_sum_indices([-1, -2, -3, -4, -5], -8) == (2, 4)"
            ),
            BlockFeedbackPythonTest(
              name = "duplicates",
              code = "assert two_sum_indices([3, 3], 6) == (0, 1)",
              hint = Some("Du darfst dasselbe Element nicht zweimal benutzen; Indizes müssen verschieden sein.")
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "unordered_solution",
              code = "assert two_sum_indices([1, 2, 4, 8], 6) == (1, 2)",
              weight = 2.0
            ),
            BlockFeedbackPythonTest(
              name = "larger",
              code = "assert two_sum_indices([10, 22, 5, 7, 19, 3], 29) == (1, 3)",
              weight = 2.0
            )
          ),
          fixtures = Nil,
          packages = Nil,
          timeoutMs = 4000
        )
    )

  val byExerciseId: Map[String, FeedbackExerciseDefinition] =
    Map(
      addTwoNumbersExerciseId -> addTwoNumbers,
      maxInListExerciseId -> maxInList,
      balancedBracketsExerciseId -> balancedBrackets,
      twoSumIndicesExerciseId -> twoSumIndices
    )

  /** Lookup an exercise definition by id. */
  def getExercise(exerciseId: String): Option[FeedbackExerciseDefinition] =
    byExerciseId.get(exerciseId)

  /** List all known feedback exercises (stable order). */
  def listExercises: Seq[FeedbackExerciseDefinition] =
    byExerciseId.values.toSeq.sortBy(_.id)
}
