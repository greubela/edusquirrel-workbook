package interactionPlugins.blockEnvironment.feedback

import it.evadid.core.datastructures.language.AppLanguage
/**
 * In-memory registry of feedback exercise definitions.
 *
 * This keeps per-exercise feedback configuration close to the exercise id and
 * optionally includes the task statement translations.
 * not all import are supported in pyodide
 */
object BlockFeedbackExerciseRegistry {

  // @ids ──────────────────────────────────────────────────────────────────
  // Stable string keys – used in persistence and routing. Never rename these.
  // ────────────────────────────────────────────────────────────────────────
  val addTwoNumbersExerciseId: String = "block:add-two-numbers"
  val maxInListExerciseId: String = "block:max-in-list"
  val balancedBracketsExerciseId: String = "block:balanced-brackets"
  val twoSumIndicesExerciseId: String = "block:two-sum-indices"

  val palindromeExerciseId: String = "block:is-palindrome"
  val gcdExerciseId: String = "block:gcd"
  val countVowelsExerciseId: String = "block:count-vowels"
  val runLengthEncodeExerciseId: String = "block:run-length-encode"
  val mergeSortedExerciseId: String = "block:merge-sorted"
  val uniquePreserveOrderExerciseId: String = "block:unique-preserve-order"
  val romanToIntExerciseId: String = "block:roman-to-int"
  val intToRomanExerciseId: String = "block:int-to-roman"
  val normalizeWhitespaceExerciseId: String = "block:normalize-whitespace"
  val rotateListExerciseId: String = "block:rotate-list"
  val flattenNestedExerciseId: String = "block:flatten-nested"
  val caesarCipherExerciseId: String = "block:caesar-cipher"

  // Script exercises (no function required)
  val fizzBuzzScriptExerciseId: String = "script:fizzbuzz"
  val evenSquaresScriptExerciseId: String = "script:even-squares"
  val fibonacciScriptExerciseId: String = "script:fibonacci"
  val primesScriptExerciseId: String = "script:primes"
  val wordCountScriptExerciseId: String = "script:word-count"

  private val english = AppLanguage.English
  private val german = AppLanguage.German

  // @exercise val=addTwoNumbers id=block:add-two-numbers
  // ──────────────────────────────────────────────────────────────────────
  // Add two numbers  ·  block:add-two-numbers
  // ──────────────────────────────────────────────────────────────────────
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
              hint = Some("Test simple positive numbers first."),
              hintDE = Some("Teste zuerst einfache positive Zahlen.")
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
              hint = Some("Also handle negative numbers."),
              hintDE = Some("Achte auch auf negative Zahlen.")
            )
          ),
          fixtures = Nil,
          packages = Nil,
          timeoutMs = 4000
        )
    )
  // @end addTwoNumbers

  // @exercise val=maxInList id=block:max-in-list
  // ──────────────────────────────────────────────────────────────────────
  // Maximum in a list  ·  block:max-in-list
  // ──────────────────────────────────────────────────────────────────────
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
              weight = 2.0,
              hint = Some("Your function must also work when all values are negative."),
              hintDE = Some("Deine Funktion muss auch funktionieren, wenn alle Werte negativ sind.")
            )
          ),
          fixtures = Nil,
          packages = Nil,
          timeoutMs = 4000
        )
    )
  // @end maxInList

  // @exercise val=balancedBrackets id=block:balanced-brackets
  // ──────────────────────────────────────────────────────────────────────
  // Balanced brackets  ·  block:balanced-brackets
  // ──────────────────────────────────────────────────────────────────────
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
              hint = Some("Pay attention to matching bracket types and order."),
              hintDE = Some("Achte auf passende Klammer-Typen und Reihenfolge.")
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
              weight = 2.0,
              hint = Some("Non-bracket characters must be completely ignored."),
              hintDE = Some("Nicht-Klammer-Zeichen müssen vollständig ignoriert werden.")
            ),
            BlockFeedbackPythonTest(
              name = "closing_first",
              code = "assert balanced_brackets(\")(\") == False",
              weight = 2.0,
              hint = Some("A closing bracket that appears before any matching opener is unbalanced."),
              hintDE = Some("Eine schließende Klammer ohne vorherige passende öffnende Klammer ist nicht ausgeglichen.")
            ),
            BlockFeedbackPythonTest(
              name = "deep_nesting",
              code = "assert balanced_brackets(\"(((())))[]{}\") == True",
              weight = 2.0,
              hint = Some("Deeply nested and consecutive balanced groups must all be handled correctly."),
              hintDE = Some("Tief verschachtelte und aufeinanderfolgende Klammergruppen müssen alle korrekt behandelt werden.")
            )
          ),
          fixtures = Nil,
          packages = Nil,
          timeoutMs = 4000
        )
    )
  // @end balancedBrackets

  // @exercise val=twoSumIndices id=block:two-sum-indices
  // ──────────────────────────────────────────────────────────────────────
  // Two-sum indices  ·  block:two-sum-indices
  // ──────────────────────────────────────────────────────────────────────
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
              hint = Some("You may not use the same element twice; indices must be different."),
              hintDE = Some("Du darfst dasselbe Element nicht zweimal benutzen; Indizes müssen verschieden sein.")
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "unordered_solution",
              code = "assert two_sum_indices([1, 2, 4, 8], 6) == (1, 2)",
              weight = 2.0,
              hint = Some("The solution pair is not always at the start of the list."),
              hintDE = Some("Das gesuchte Paar steht nicht immer am Anfang der Liste.")
            ),
            BlockFeedbackPythonTest(
              name = "larger",
              code = "res = two_sum_indices([10, 22, 5, 7, 19, 3], 29)\nassert res in [(0, 4), (1, 3)]",
              weight = 2.0,
              hint = Some("Your function must correctly search through a longer list to find the matching pair."),
              hintDE = Some("Deine Funktion muss auch in einer längeren Liste das passende Paar finden.")
            )
          ),
          fixtures = Nil,
          packages = Nil,
          timeoutMs = 4000
        )
    )
  // @end twoSumIndices

  // @exercise val=palindrome id=block:is-palindrome
  // ──────────────────────────────────────────────────────────────────────
  // Palindrome check  ·  block:is-palindrome
  // ──────────────────────────────────────────────────────────────────────
  val palindrome: FeedbackExerciseDefinition =
    FeedbackExerciseDefinition(
      id = palindromeExerciseId,
      titleTranslations = Map(
        english -> "Palindrome check",
        german -> "Palindrome prüfen"
      ),
      statementTranslations = Map(
        english -> "Implement `is_palindrome(s)` that returns True iff `s` reads the same forwards and backwards. Ignore case and all non-alphanumeric characters.",
        german -> "Implementiere `is_palindrome(s)`, das True zurückgibt genau dann, wenn `s` vorwärts und rückwärts gleich ist. Ignoriere Groß-/Kleinschreibung und alle nicht-alphanumerischen Zeichen."
      ),
      config =
        BlockFeedbackConfig(
          enableVmStaticChecks = true,
          enablePythonStaticChecks = true,
          enableUnitTests = true,
          enableAiSummary = true,
          visibleTests = Seq(
            BlockFeedbackPythonTest(
              name = "simple_true",
              code = "assert is_palindrome('level') == True"
            ),
            BlockFeedbackPythonTest(
              name = "simple_false",
              code = "assert is_palindrome('hello') == False"
            ),
            BlockFeedbackPythonTest(
              name = "ignore_case",
              code = "assert is_palindrome('RaceCar') == True",
              hint = Some("Remember case-insensitive comparison."),
              hintDE = Some("Denk an Groß-/Kleinschreibung.")
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "ignore_punct",
              code = "assert is_palindrome('A man, a plan, a canal: Panama!') == True",
              weight = 2.0,
              hint = Some("Ignore non-alphanumeric characters."),
              hintDE = Some("Ignoriere Nicht-Buchstaben/Ziffern.")
            ),
            BlockFeedbackPythonTest(
              name = "digits_mix",
              code = "assert is_palindrome('12-21') == True",
              weight = 2.0,
              hint = Some("Digits count as alphanumeric; non-alphanumeric characters like hyphens must be stripped."),
              hintDE = Some("Ziffern zählen als alphanumerisch; Nicht-Buchstaben/Ziffern wie Bindestriche müssen entfernt werden.")
            )
          ),
          fixtures = Nil,
          packages = Nil,
          timeoutMs = 4000
        )
    )
  // @end palindrome

  // @exercise val=gcd id=block:gcd
  // ──────────────────────────────────────────────────────────────────────
  // Greatest common divisor  ·  block:gcd
  // ──────────────────────────────────────────────────────────────────────
  val gcd: FeedbackExerciseDefinition =
    FeedbackExerciseDefinition(
      id = gcdExerciseId,
      titleTranslations = Map(
        english -> "Greatest common divisor",
        german -> "Größter gemeinsamer Teiler"
      ),
      statementTranslations = Map(
        english -> "Implement `gcd(a, b)` that returns the greatest common divisor of two integers `a` and `b` (non-negative result).",
        german -> "Implementiere `gcd(a, b)`, das den größten gemeinsamen Teiler von zwei ganzen Zahlen `a` und `b` zurückgibt (nicht-negatives Ergebnis)."
      ),
      config =
        BlockFeedbackConfig(
          enableVmStaticChecks = true,
          enablePythonStaticChecks = true,
          enableUnitTests = true,
          enableAiSummary = true,
          visibleTests = Seq(
            BlockFeedbackPythonTest(
              name = "basic",
              code = "assert gcd(12, 18) == 6"
            ),
            BlockFeedbackPythonTest(
              name = "co_prime",
              code = "assert gcd(17, 29) == 1"
            ),
            BlockFeedbackPythonTest(
              name = "with_zero",
              code = "assert gcd(0, 5) == 5",
              hint = Some("Define gcd(0, n) sensibly."),
              hintDE = Some("Definiere gcd(0, n) sinnvoll.")
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "negative_inputs",
              code = "assert gcd(-12, 18) == 6",
              weight = 2.0,
              hint = Some("Handle negative inputs correctly."),
              hintDE = Some("Achte auf negatives Vorzeichen.")
            ),
            BlockFeedbackPythonTest(
              name = "both_zero",
              code = "assert gcd(0, 0) == 0",
              weight = 2.0,
              hint = Some("gcd(0, 0) should return 0 by convention."),
              hintDE = Some("gcd(0, 0) soll per Konvention 0 zurückgeben.")
            )
          ),
          fixtures = Nil,
          packages = Nil,
          timeoutMs = 4000
        )
    )
  // @end gcd

  // @exercise val=countVowels id=block:count-vowels
  // ──────────────────────────────────────────────────────────────────────
  // Count vowels  ·  block:count-vowels
  // ──────────────────────────────────────────────────────────────────────
  val countVowels: FeedbackExerciseDefinition =
    FeedbackExerciseDefinition(
      id = countVowelsExerciseId,
      titleTranslations = Map(
        english -> "Count vowels",
        german -> "Vokale zählen"
      ),
      statementTranslations = Map(
        english -> "Implement `count_vowels(s)` that returns how many vowels (a,e,i,o,u) occur in `s` (case-insensitive).",
        german -> "Implementiere `count_vowels(s)`, das zurückgibt, wie viele Vokale (a,e,i,o,u) in `s` vorkommen (Groß-/Kleinschreibung egal)."
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
              code = "assert count_vowels('') == 0"
            ),
            BlockFeedbackPythonTest(
              name = "simple",
              code = "assert count_vowels('hello') == 2"
            ),
            BlockFeedbackPythonTest(
              name = "case",
              code = "assert count_vowels('EduSquirrel') == 5",
              hint = Some("Count case-insensitively."),
              hintDE = Some("Case-insensitive zählen.")
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "spaces_and_punct",
              code = "assert count_vowels('A, E! I? O. U') == 5",
              weight = 2.0,
              hint = Some("Punctuation and spaces are not vowels and must not be counted."),
              hintDE = Some("Satzzeichen und Leerzeichen sind keine Vokale und dürfen nicht gezählt werden.")
            ),
            BlockFeedbackPythonTest(
              name = "no_vowels",
              code = "assert count_vowels('rhythms') == 0",
              weight = 2.0,
              hint = Some("A string with no vowels at all should return 0."),
              hintDE = Some("Ein String ohne Vokale muss 0 zurückgeben.")
            )
          ),
          fixtures = Nil,
          packages = Nil,
          timeoutMs = 4000
        )
    )
  // @end countVowels

  // @exercise val=runLengthEncode id=block:run-length-encode
  // ──────────────────────────────────────────────────────────────────────
  // Run-length encoding  ·  block:run-length-encode
  // ──────────────────────────────────────────────────────────────────────
  val runLengthEncode: FeedbackExerciseDefinition =
    FeedbackExerciseDefinition(
      id = runLengthEncodeExerciseId,
      titleTranslations = Map(
        english -> "Run-length encoding",
        german -> "Lauflängenkodierung"
      ),
      statementTranslations = Map(
        english -> "Implement `rle_encode(s)` that returns a run-length encoding of `s` as a list of tuples `(char, count)`.",
        german -> "Implementiere `rle_encode(s)`, das eine Lauflängenkodierung von `s` als Liste von Tupeln `(zeichen, anzahl)` zurückgibt."
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
              code = "assert rle_encode('') == []"
            ),
            BlockFeedbackPythonTest(
              name = "single",
              code = "assert rle_encode('a') == [('a', 1)]"
            ),
            BlockFeedbackPythonTest(
              name = "basic",
              code = "assert rle_encode('aaabbc') == [('a', 3), ('b', 2), ('c', 1)]",
              hint = Some("Think about transitions between characters."),
              hintDE = Some("Denk an Wechsel zwischen Zeichen.")
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "with_spaces",
              code = "assert rle_encode('  !!') == [(' ', 2), ('!', 2)]",
              weight = 2.0,
              hint = Some("Spaces and special characters must be encoded just like letters."),
              hintDE = Some("Leerzeichen und Sonderzeichen müssen genauso kodiert werden wie Buchstaben.")
            ),
            BlockFeedbackPythonTest(
              name = "long_run",
              code = "assert rle_encode('zzzzzzzzzz') == [('z', 10)]",
              weight = 2.0,
              hint = Some("A run of the same character must be counted correctly regardless of its length."),
              hintDE = Some("Eine Folge gleicher Zeichen muss unabhängig von ihrer Länge korrekt gezählt werden.")
            )
          ),
          fixtures = Nil,
          packages = Nil,
          timeoutMs = 5000
        )
    )
  // @end runLengthEncode

  // @exercise val=mergeSorted id=block:merge-sorted
  // ──────────────────────────────────────────────────────────────────────
  // Merge two sorted lists  ·  block:merge-sorted
  // ──────────────────────────────────────────────────────────────────────
  val mergeSorted: FeedbackExerciseDefinition =
    FeedbackExerciseDefinition(
      id = mergeSortedExerciseId,
      titleTranslations = Map(
        english -> "Merge two sorted lists",
        german -> "Zwei sortierte Listen mergen"
      ),
      statementTranslations = Map(
        english -> "Implement `merge_sorted(a, b)` that merges two sorted lists `a` and `b` into a new sorted list.",
        german -> "Implementiere `merge_sorted(a, b)`, das zwei sortierte Listen `a` und `b` zu einer neuen sortierten Liste zusammenführt."
      ),
      config =
        BlockFeedbackConfig(
          enableVmStaticChecks = true,
          enablePythonStaticChecks = true,
          enableUnitTests = true,
          enableAiSummary = true,
          visibleTests = Seq(
            BlockFeedbackPythonTest(
              name = "both_empty",
              code = "assert merge_sorted([], []) == []"
            ),
            BlockFeedbackPythonTest(
              name = "simple",
              code = "assert merge_sorted([1, 3, 5], [2, 4, 6]) == [1, 2, 3, 4, 5, 6]"
            ),
            BlockFeedbackPythonTest(
              name = "duplicates",
              code = "assert merge_sorted([1, 2, 2], [2, 2, 3]) == [1, 2, 2, 2, 2, 3]",
              hint = Some("Do not lose duplicates."),
              hintDE = Some("Du darfst Duplikate nicht verlieren.")
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "one_empty",
              code = "assert merge_sorted([], [1, 2, 3]) == [1, 2, 3]",
              weight = 2.0,
              hint = Some("When one list is empty the result should equal the other list."),
              hintDE = Some("Wenn eine Liste leer ist, soll das Ergebnis gleich der anderen Liste sein.")
            ),
            BlockFeedbackPythonTest(
              name = "negatives",
              code = "assert merge_sorted([-5, -1, 0], [-6, -2, 3]) == [-6, -5, -2, -1, 0, 3]",
              weight = 2.0,
              hint = Some("Your merge must preserve correct order even when negative numbers are involved."),
              hintDE = Some("Dein Merge muss die korrekte Reihenfolge auch bei negativen Zahlen erhalten.")
            )
          ),
          fixtures = Nil,
          packages = Nil,
          timeoutMs = 5000
        )
    )
  // @end mergeSorted

  // @exercise val=uniquePreserveOrder id=block:unique-preserve-order
  // ──────────────────────────────────────────────────────────────────────
  // Unique (preserve order)  ·  block:unique-preserve-order
  // ──────────────────────────────────────────────────────────────────────
  val uniquePreserveOrder: FeedbackExerciseDefinition =
    FeedbackExerciseDefinition(
      id = uniquePreserveOrderExerciseId,
      titleTranslations = Map(
        english -> "Unique (preserve order)",
        german -> "Einzigartige Elemente (Reihenfolge behalten)"
      ),
      statementTranslations = Map(
        english -> "Implement `unique(xs)` that returns a new list with duplicates removed, keeping the first occurrence order.",
        german -> "Implementiere `unique(xs)`, das eine neue Liste ohne Duplikate zurückgibt und dabei die Reihenfolge der ersten Vorkommen beibehält."
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
              code = "assert unique([]) == []"
            ),
            BlockFeedbackPythonTest(
              name = "basic",
              code = "assert unique([1, 2, 2, 3, 1]) == [1, 2, 3]"
            ),
            BlockFeedbackPythonTest(
              name = "strings",
              code = "assert unique(['a', 'b', 'a', 'c', 'b']) == ['a', 'b', 'c']",
              hint = Some("Keep the order of first occurrences."),
              hintDE = Some("Reihenfolge der ersten Vorkommen behalten.")
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "already_unique",
              code = "assert unique([5, 4, 3]) == [5, 4, 3]",
              weight = 2.0,
              hint = Some("A list that is already duplicate-free must be returned unchanged."),
              hintDE = Some("Eine Liste ohne Duplikate muss unverändert zurückgegeben werden.")
            ),
            BlockFeedbackPythonTest(
              name = "with_zero",
              code = "assert unique([0, 0, 0, 1, 0]) == [0, 1]",
              weight = 2.0,
              hint = Some("Zero counts as a value and duplicates of it must be removed too."),
              hintDE = Some("Null ist ein gültiger Wert und Duplikate davon müssen ebenfalls entfernt werden.")
            )
          ),
          fixtures = Nil,
          packages = Nil,
          timeoutMs = 5000
        )
    )
  // @end uniquePreserveOrder

  // @exercise val=romanToInt id=block:roman-to-int
  // ──────────────────────────────────────────────────────────────────────
  // Roman to integer  ·  block:roman-to-int
  // ──────────────────────────────────────────────────────────────────────
  val romanToInt: FeedbackExerciseDefinition =
    FeedbackExerciseDefinition(
      id = romanToIntExerciseId,
      titleTranslations = Map(
        english -> "Roman to integer",
        german -> "Römisch zu Zahl"
      ),
      statementTranslations = Map(
        english -> "Implement `roman_to_int(s)` converting a Roman numeral (I,V,X,L,C,D,M) to an integer. You may assume valid input (1..3999).",
        german -> "Implementiere `roman_to_int(s)`, das eine römische Zahl (I,V,X,L,C,D,M) in eine ganze Zahl umwandelt. Du darfst gültige Eingaben (1..3999) annehmen."
      ),
      config =
        BlockFeedbackConfig(
          enableVmStaticChecks = true,
          enablePythonStaticChecks = true,
          enableUnitTests = true,
          enableAiSummary = true,
          visibleTests = Seq(
            BlockFeedbackPythonTest(
              name = "simple",
              code = "assert roman_to_int('III') == 3"
            ),
            BlockFeedbackPythonTest(
              name = "subtractive",
              code = "assert roman_to_int('IV') == 4",
              hint = Some("Pay attention to subtractive notation: IV, IX, XL, ..."),
              hintDE = Some("Achte auf Subtraktionsregeln wie IV, IX, XL, ...")
            ),
            BlockFeedbackPythonTest(
              name = "mixed",
              code = "assert roman_to_int('MCMXCIV') == 1994"
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "max",
              code = "assert roman_to_int('MMMCMXCIX') == 3999",
              weight = 2.0,
              hint = Some("Your function must handle the maximum valid Roman numeral (MMMCMXCIX = 3999) correctly."),
              hintDE = Some("Deine Funktion muss auch die größte gültige römische Zahl (MMMCMXCIX = 3999) korrekt umwandeln.")
            ),
            BlockFeedbackPythonTest(
              name = "many",
              code = "assert roman_to_int('CDXLIV') == 444",
              weight = 2.0,
              hint = Some("Multiple subtractive pairs in one number (CD, XL, IV) must all be handled."),
              hintDE = Some("Mehrere Subtraktionspaare in einer Zahl (CD, XL, IV) müssen alle korrekt behandelt werden.")
            )
          ),
          fixtures = Nil,
          packages = Nil,
          timeoutMs = 5000
        )
    )
  // @end romanToInt

  // @exercise val=intToRoman id=block:int-to-roman
  // ──────────────────────────────────────────────────────────────────────
  // Integer to Roman  ·  block:int-to-roman
  // ──────────────────────────────────────────────────────────────────────
  val intToRoman: FeedbackExerciseDefinition =
    FeedbackExerciseDefinition(
      id = intToRomanExerciseId,
      titleTranslations = Map(
        english -> "Integer to Roman",
        german -> "Zahl zu Römisch"
      ),
      statementTranslations = Map(
        english -> "Implement `int_to_roman(n)` converting an integer `n` (1..3999) to a Roman numeral using standard subtractive notation.",
        german -> "Implementiere `int_to_roman(n)`, das eine ganze Zahl `n` (1..3999) in eine römische Zahl mit Standard-Subtraktionsschreibweise umwandelt."
      ),
      config =
        BlockFeedbackConfig(
          enableVmStaticChecks = true,
          enablePythonStaticChecks = true,
          enableUnitTests = true,
          enableAiSummary = true,
          visibleTests = Seq(
            BlockFeedbackPythonTest(
              name = "one",
              code = "assert int_to_roman(1) == 'I'"
            ),
            BlockFeedbackPythonTest(
              name = "subtractive",
              code = "assert int_to_roman(4) == 'IV'",
              hint = Some("Use subtractive notation (IV, IX, XL, ...)."),
              hintDE = Some("Nutze die Subtraktionsschreibweise (IV, IX, XL, ...).")
            ),
            BlockFeedbackPythonTest(
              name = "mixed",
              code = "assert int_to_roman(1994) == 'MCMXCIV'"
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "max",
              code = "assert int_to_roman(3999) == 'MMMCMXCIX'",
              weight = 2.0,
              hint = Some("Your function must produce the correct Roman numeral for the maximum value 3999."),
              hintDE = Some("Deine Funktion muss für den Maximalwert 3999 die korrekte römische Zahl erzeugen.")
            ),
            BlockFeedbackPythonTest(
              name = "444",
              code = "assert int_to_roman(444) == 'CDXLIV'",
              weight = 2.0,
              hint = Some("Numbers with multiple subtractive steps (CD=400, XL=40, IV=4) must all be converted correctly."),
              hintDE = Some("Zahlen mit mehreren Subtraktionsschritten (CD=400, XL=40, IV=4) müssen alle korrekt umgewandelt werden.")
            )
          ),
          fixtures = Nil,
          packages = Nil,
          timeoutMs = 5000
        )
    )
  // @end intToRoman

  // @exercise val=normalizeWhitespace id=block:normalize-whitespace
  // ──────────────────────────────────────────────────────────────────────
  // Normalize whitespace  ·  block:normalize-whitespace
  // ──────────────────────────────────────────────────────────────────────
  val normalizeWhitespace: FeedbackExerciseDefinition =
    FeedbackExerciseDefinition(
      id = normalizeWhitespaceExerciseId,
      titleTranslations = Map(
        english -> "Normalize whitespace",
        german -> "Whitespace normalisieren"
      ),
      statementTranslations = Map(
        english -> "Implement `normalize_whitespace(s)` that trims leading/trailing whitespace and replaces any internal whitespace runs with a single space.",
        german -> "Implementiere `normalize_whitespace(s)`, das führende/abschließende Leerzeichen entfernt und interne Whitespace-Folgen durch genau ein Leerzeichen ersetzt."
      ),
      config =
        BlockFeedbackConfig(
          enableVmStaticChecks = true,
          enablePythonStaticChecks = true,
          enableUnitTests = true,
          enableAiSummary = true,
          visibleTests = Seq(
            BlockFeedbackPythonTest(
              name = "already_clean",
              code = "assert normalize_whitespace('hello world') == 'hello world'"
            ),
            BlockFeedbackPythonTest(
              name = "trim",
              code = "assert normalize_whitespace('  hello  ') == 'hello'"
            ),
            BlockFeedbackPythonTest(
              name = "collapse",
              code = "assert normalize_whitespace('a\\t\\t b\\n  c') == 'a b c'",
              hint = Some("All whitespace types count (tab, newline, ...)."),
              hintDE = Some("Alle Whitespace-Arten zählen (Tab, Newline, ...).")
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "only_space",
              code = "assert normalize_whitespace('    ') == ''",
              weight = 2.0,
              hint = Some("A string containing only whitespace should become an empty string."),
              hintDE = Some("Ein String, der nur aus Whitespace besteht, soll zu einem leeren String werden.")
            ),
            BlockFeedbackPythonTest(
              name = "mixed_unicode_space",
              code = "assert normalize_whitespace('x\\n\\n\\ty') == 'x y'",
              weight = 2.0,
              hint = Some("Multiple consecutive whitespace characters of any kind (spaces, tabs, newlines) must collapse to a single space."),
              hintDE = Some("Mehrere aufeinanderfolgende Whitespace-Zeichen jeder Art (Leerzeichen, Tabs, Zeilenumbrüche) müssen zu einem einzigen Leerzeichen zusammengefasst werden.")
            )
          ),
          fixtures = Nil,
          packages = Nil,
          timeoutMs = 4000
        )
    )
  // @end normalizeWhitespace

  // @exercise val=rotateList id=block:rotate-list
  // ──────────────────────────────────────────────────────────────────────
  // Rotate list  ·  block:rotate-list
  // ──────────────────────────────────────────────────────────────────────
  val rotateList: FeedbackExerciseDefinition =
    FeedbackExerciseDefinition(
      id = rotateListExerciseId,
      titleTranslations = Map(
        english -> "Rotate list",
        german -> "Liste rotieren"
      ),
      statementTranslations = Map(
        english -> "Implement `rotate(xs, k)` that rotates list `xs` to the right by `k` steps and returns the new list. `k` may be larger than the length.",
        german -> "Implementiere `rotate(xs, k)`, das die Liste `xs` um `k` Schritte nach rechts rotiert und die neue Liste zurückgibt. `k` darf größer als die Länge sein."
      ),
      config =
        BlockFeedbackConfig(
          enableVmStaticChecks = true,
          enablePythonStaticChecks = true,
          enableUnitTests = true,
          enableAiSummary = true,
          visibleTests = Seq(
            BlockFeedbackPythonTest(
              name = "k_zero",
              code = "assert rotate([1,2,3], 0) == [1,2,3]"
            ),
            BlockFeedbackPythonTest(
              name = "basic",
              code = "assert rotate([1,2,3,4,5], 2) == [4,5,1,2,3]"
            ),
            BlockFeedbackPythonTest(
              name = "k_larger",
              code = "assert rotate([1,2,3], 10) == [3,1,2]",
              hint = Some("Use k % len(xs)."),
              hintDE = Some("Nutze k % len(xs).")
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "empty",
              code = "assert rotate([], 3) == []",
              weight = 2.0,
              hint = Some("Empty list is a special case."),
              hintDE = Some("Leere Liste ist Spezialfall.")
            ),
            BlockFeedbackPythonTest(
              name = "single",
              code = "assert rotate([42], 999) == [42]",
              weight = 2.0,
              hint = Some("Rotating a single-element list by any amount must return that same list."),
              hintDE = Some("Eine Liste mit einem einzigen Element muss bei jeder Rotation unverändert bleiben.")
            )
          ),
          fixtures = Nil,
          packages = Nil,
          timeoutMs = 5000
        )
    )
  // @end rotateList

  // @exercise val=fizzBuzzScript id=script:fizzbuzz
  // ──────────────────────────────────────────────────────────────────────
  // FizzBuzz Script  ·  script:fizzbuzz
  // ──────────────────────────────────────────────────────────────────────
  val fizzBuzzScript: FeedbackExerciseDefinition =
    FeedbackExerciseDefinition(
      id = fizzBuzzScriptExerciseId,
      titleTranslations = Map(
        english -> "FizzBuzz (Script)",
        german  -> "FizzBuzz (Script)"
      ),
      statementTranslations = Map(
        english -> ("Write a script that iterates over the numbers 1 to 20 " +
          "and appends to a list `ergebnisse`: `\"Fizz\"` for multiples of 3, `\"Buzz\"` " +
          "for multiples of 5, `\"FizzBuzz\"` for multiples of both, and the number itself otherwise."),
        german -> ("Schreibe ein Script, das die Zahlen 1 bis 20 durchläuft " +
          "und in eine Liste `ergebnisse` schreibt: `\"Fizz\"` für Vielfache von 3, `\"Buzz\"` " +
          "für Vielfache von 5, `\"FizzBuzz\"` für Vielfache von beiden, sonst die Zahl selbst.")
      ),
      config = BlockFeedbackConfig(
        enableVmStaticChecks = true,
        enablePythonStaticChecks = true,
        enableUnitTests = true,
        enableAiSummary = true,
        visibleTests = Seq(
          BlockFeedbackPythonTest(
            name = "fizz",
            code = """assert ergebnisse[2] == "Fizz" """,
            hint = Some("Index 2 is number 3 – it should give \"Fizz\"."),
            hintDE = Some("Index 2 entspricht der Zahl 3 – sie sollte \"Fizz\" ergeben.")
          ),
          BlockFeedbackPythonTest(
            name = "buzz",
            code = """assert ergebnisse[4] == "Buzz" """,
            hint = Some("Index 4 is number 5 – it should give \"Buzz\"."),
            hintDE = Some("Index 4 entspricht der Zahl 5 – sie sollte \"Buzz\" ergeben.")
          )
        ),
        hiddenTests = Seq(
          BlockFeedbackPythonTest(
            name = "fizzbuzz",
            code = """assert ergebnisse[14] == "FizzBuzz" """,
            weight = 2.0,
            hint = Some("Index 14 = number 15 – multiple of both 3 and 5 → \"FizzBuzz\"."),
            hintDE = Some("Index 14 = Zahl 15 – Vielfaches von 3 und 5 → \"FizzBuzz\".")
          ),
          BlockFeedbackPythonTest(
            name = "length",
            code = "assert len(ergebnisse) == 20",
            weight = 1.0,
            hint = Some("ergebnisse should have exactly 20 entries."),
            hintDE = Some("ergebnisse soll genau 20 Einträge haben.")
          ),
          BlockFeedbackPythonTest(
            name = "number",
            code = "assert ergebnisse[0] == 1",
            weight = 1.0,
            hint = Some("Numbers not divisible by 3 or 5 go directly into the list."),
            hintDE = Some("Zahlen die weder durch 3 noch 5 teilbar sind, kommen direkt in die Liste.")
          ),
          BlockFeedbackPythonTest(
            name = "full",
            code = """assert ergebnisse == [1, 2, "Fizz", 4, "Buzz", "Fizz", 7, 8, "Fizz", "Buzz", 11, "Fizz", 13, 14, "FizzBuzz", 16, 17, "Fizz", 19, "Buzz"]""",
            weight = 2.0,
            hint = Some("The complete list is wrong. Check all entries for correct Fizz/Buzz rules."),
            hintDE = Some("Die vollständige Liste stimmt nicht. Prüfe alle Einträge auf die richtigen Fizz/Buzz-Regeln.")
          ),
          BlockFeedbackPythonTest(
            name = "no_hardcode",
            code =
              """import ast as _ast
                |_tree = _ast.parse(_student_source)
                |_hardcoded = [
                |    n for n in _ast.walk(_tree)
                |    if isinstance(n, _ast.Assign)
                |    and any(isinstance(t, _ast.Name) and t.id == 'ergebnisse' for t in n.targets)
                |    and isinstance(n.value, _ast.List)
                |    and len(n.value.elts) >= 5
                |]
                |assert not _hardcoded
                |""".stripMargin,
            weight = 1.0,
            hint = Some("Build the list with a loop over 1\u201320, do not hard-code the list literal."),
            hintDE = Some("Baue die Liste mit einer Schleife \u00fcber 1\u201320 auf, nicht durch direktes Eintippen.")
          )
        ),
        fixtures = Nil,
        packages = Nil,
        timeoutMs = 4000,
        isScriptExercise = true
      )
    )
  // @end fizzBuzzScript

  // @exercise val=evenSquaresScript id=script:even-squares
  // ──────────────────────────────────────────────────────────────────────
  // Even Squares Script  ·  script:even-squares
  // ──────────────────────────────────────────────────────────────────────
  val evenSquaresScript: FeedbackExerciseDefinition =
    FeedbackExerciseDefinition(
      id = evenSquaresScriptExerciseId,
      titleTranslations = Map(
        english -> "Even Squares (Script)",
        german  -> "Quadrate gerader Zahlen (Script)"
      ),
      statementTranslations = Map(
        english -> ("Write a script that takes the numbers `1` to `20`, " +
          "filters out all odd numbers, and stores the squares of the remaining even numbers " +
          "in a list called `ergebnisse`."),
        german -> ("Schreibe ein Script, das die Zahlen `1` bis `20` durchläuft, " +
          "alle ungeraden Zahlen herausfiltert und die Quadrate der geraden Zahlen " +
          "in einer Liste `ergebnisse` speichert.")
      ),
      config = BlockFeedbackConfig(
        enableVmStaticChecks = true,
        enablePythonStaticChecks = true,
        enableUnitTests = true,
        enableAiSummary = true,
        visibleTests = Seq(
          BlockFeedbackPythonTest(
            name = "result",
            code = "assert ergebnisse == [4, 16, 36, 64, 100, 144, 196, 256, 324, 400]",
            hint = Some("Expected: squares of even numbers from 2 to 20 → [4, 16, 36, 64, 100, 144, 196, 256, 324, 400]."),
            hintDE = Some("Quadrate der geraden Zahlen von 2 bis 20: [4, 16, 36, 64, 100, 144, 196, 256, 324, 400].")
          )
        ),
        hiddenTests = Seq(
          BlockFeedbackPythonTest(
            name = "is_list",
            code = "assert isinstance(ergebnisse, list)",
            weight = 1.0,
            hint = Some("ergebnisse must be of type list."),
            hintDE = Some("ergebnisse muss vom Typ list sein.")
          ),
          BlockFeedbackPythonTest(
            name = "length",
            code = "assert len(ergebnisse) == 10",
            weight = 1.0,
            hint = Some("There are exactly 10 even numbers between 1 and 20."),
            hintDE = Some("Es gibt genau 10 gerade Zahlen zwischen 1 und 20.")
          ),
          BlockFeedbackPythonTest(
            name = "no_hardcode",
            code =
              """import ast as _ast
                |_tree = _ast.parse(_student_source)
                |_hardcoded = [
                |    n for n in _ast.walk(_tree)
                |    if isinstance(n, _ast.Assign)
                |    and any(isinstance(t, _ast.Name) and t.id == 'ergebnisse' for t in n.targets)
                |    and isinstance(n.value, _ast.List)
                |    and len(n.value.elts) >= 5
                |]
                |assert not _hardcoded
                |""".stripMargin,
            weight = 1.0,
            hint = Some("Compute the squares with a loop or list comprehension, do not hard-code the list literal."),
            hintDE = Some("Berechne die Quadrate mit einer Schleife oder List Comprehension, nicht durch direktes Eintippen der Liste.")
          )
        ),
        fixtures = Nil,
        packages = Nil,
        timeoutMs = 4000,
        isScriptExercise = true
      )
    )
  // @end evenSquaresScript

  // @exercise val=fibonacciScript id=script:fibonacci
  // ──────────────────────────────────────────────────────────────────────
  // Fibonacci Script  ·  script:fibonacci
  // ──────────────────────────────────────────────────────────────────────
  val fibonacciScript: FeedbackExerciseDefinition =
    FeedbackExerciseDefinition(
      id = fibonacciScriptExerciseId,
      titleTranslations = Map(
        english -> "Fibonacci (Script)",
        german  -> "Fibonacci (Script)"
      ),
      statementTranslations = Map(
        english -> ("Write a script that computes the first 10 Fibonacci numbers " +
          "and stores them in a list called `fibonacci`. " +
          "The sequence starts with 1 and 1; every following number is the sum of the two preceding ones."),
        german -> ("Schreibe ein Script, das die ersten 10 Fibonacci-Zahlen berechnet " +
          "und in einer Liste `fibonacci` speichert. " +
          "Die Folge beginnt mit 1 und 1; jede weitere Zahl ist die Summe der beiden vorherigen.")
      ),
      config = BlockFeedbackConfig(
        enableVmStaticChecks = true,
        enablePythonStaticChecks = true,
        enableUnitTests = true,
        enableAiSummary = true,
        visibleTests = Seq(
          BlockFeedbackPythonTest(
            name = "start",
            code = "assert fibonacci[0] == 1 and fibonacci[1] == 1",
            hint = Some("The sequence starts with 1, 1."),
            hintDE = Some("Die Folge startet mit 1, 1.")
          ),
          BlockFeedbackPythonTest(
            name = "tenth",
            code = "assert fibonacci[9] == 55",
            hint = Some("The 10th Fibonacci number is 55."),
            hintDE = Some("Die 10. Fibonacci-Zahl ist 55.")
          )
        ),
        hiddenTests = Seq(
          BlockFeedbackPythonTest(
            name = "full",
            code = "assert fibonacci == [1, 1, 2, 3, 5, 8, 13, 21, 34, 55]",
            weight = 2.0,
            hint = Some("Complete sequence: [1, 1, 2, 3, 5, 8, 13, 21, 34, 55]."),
            hintDE = Some("Vollständige Folge: [1, 1, 2, 3, 5, 8, 13, 21, 34, 55].")
          ),
          BlockFeedbackPythonTest(
            name = "length",
            code = "assert len(fibonacci) == 10",
            weight = 1.0,
            hint = Some("fibonacci should have exactly 10 elements."),
            hintDE = Some("fibonacci soll genau 10 Elemente haben.")
          ),
          BlockFeedbackPythonTest(
            name = "no_hardcode",
            code =
              """import ast as _ast
                |_tree = _ast.parse(_student_source)
                |_hardcoded = [
                |    n for n in _ast.walk(_tree)
                |    if isinstance(n, _ast.List)
                |    and len(n.elts) >= 5
                |    and all(isinstance(e, _ast.Constant) and isinstance(e.value, (int, float)) for e in n.elts)
                |]
                |assert not _hardcoded
                |""".stripMargin,
            weight = 1.0,
            hint = Some("Compute the Fibonacci numbers with a loop, do not hard-code the list literal."),
            hintDE = Some("Berechne die Fibonacci-Zahlen mit einer Schleife, nicht durch direktes Eintippen der Liste.")
          )
        ),
        fixtures = Nil,
        packages = Nil,
        timeoutMs = 4000,
        isScriptExercise = true
      )
    )
  // @end fibonacciScript

  // @exercise val=primesScript id=script:primes
  // ──────────────────────────────────────────────────────────────────────
  // Prime Numbers Script  ·  script:primes
  // ──────────────────────────────────────────────────────────────────────
  val primesScript: FeedbackExerciseDefinition =
    FeedbackExerciseDefinition(
      id = primesScriptExerciseId,
      titleTranslations = Map(
        english -> "Prime numbers up to 50 (Script)",
        german  -> "Primzahlen bis 50 (Script)"
      ),
      statementTranslations = Map(
        english -> ("Write a script that collects all prime numbers up to (and including) 50 " +
          "into a list called `primzahlen`, in ascending order. " +
          "A prime is a natural number greater than 1 that is divisible only by 1 and itself."),
        german -> ("Schreibe ein Script, das alle Primzahlen bis einschlie\u00dflich 50 " +
          "in aufsteigender Reihenfolge in eine Liste `primzahlen` sammelt. " +
          "Eine Primzahl ist eine nat\u00fcrliche Zahl gr\u00f6\u00dfer als 1, die nur durch 1 und sich selbst teilbar ist.")
      ),
      config = BlockFeedbackConfig(
        enableVmStaticChecks = true,
        enablePythonStaticChecks = true,
        enableUnitTests = true,
        enableAiSummary = true,
        visibleTests = Seq(
          BlockFeedbackPythonTest(
            name = "non_empty",
            code = "assert len(primzahlen) > 0",
            hint = Some("primzahlen is empty. Check your primality test: the divisor loop must start at 2, not 1 (1 divides every number)."),
            hintDE = Some("primzahlen ist leer. Prüfe deine Primzahlprüfung: Die Teiler-Schleife muss bei 2 beginnen, nicht bei 1 (1 teilt jede Zahl).")
          ),
          BlockFeedbackPythonTest(
            name = "first",
            code = "assert primzahlen[0] == 2",
            hint = Some("The smallest prime number is 2."),
            hintDE = Some("Die kleinste Primzahl ist 2.")
          ),
          BlockFeedbackPythonTest(
            name = "fourth",
            code = "assert primzahlen[3] == 7",
            hint = Some("The first four primes are 2, 3, 5, 7."),
            hintDE = Some("Die ersten vier Primzahlen sind 2, 3, 5, 7.")
          )
        ),
        hiddenTests = Seq(
          BlockFeedbackPythonTest(
            name = "count",
            code = "assert len(primzahlen) == 15",
            weight = 2.0,
            hint = Some("There are exactly 15 prime numbers \u2264 50."),
            hintDE = Some("Es gibt genau 15 Primzahlen \u2264 50.")
          ),
          BlockFeedbackPythonTest(
            name = "last",
            code = "assert primzahlen[-1] == 47",
            weight = 1.0,
            hint = Some("The largest prime \u2264 50 is 47."),
            hintDE = Some("Die gr\u00f6\u00dfte Primzahl \u2264 50 ist 47.")
          ),
          BlockFeedbackPythonTest(
            name = "all_prime",
            code =
              """def _is_prime(n):
                |    if n < 2: return False
                |    for i in range(2, int(n**0.5) + 1):
                |        if n % i == 0: return False
                |    return True
                |assert len(primzahlen) > 0, "primzahlen must not be empty"
                |assert all(_is_prime(p) for p in primzahlen)
                |assert all(2 <= p <= 50 for p in primzahlen)
                |""".stripMargin,
            weight = 2.0,
            hint = Some("Every element in primzahlen must be a prime between 2 and 50."),
            hintDE = Some("Jedes Element in primzahlen muss eine Primzahl zwischen 2 und 50 sein.")
          ),
          BlockFeedbackPythonTest(
            name = "complete",
            code =
              """def _is_prime_c(n):
                |    if n < 2: return False
                |    for i in range(2, int(n**0.5) + 1):
                |        if n % i == 0: return False
                |    return True
                |_expected = [n for n in range(2, 51) if _is_prime_c(n)]
                |assert sorted(primzahlen) == _expected
                |""".stripMargin,
            weight = 2.0,
            hint = Some("Some primes are missing or extra elements are present."),
            hintDE = Some("Einige Primzahlen fehlen oder es sind unerw\u00fcnschte Elemente enthalten.")
          ),
          BlockFeedbackPythonTest(
            name = "no_hardcode",
            code =
              """import ast as _ast
                |_tree = _ast.parse(_student_source)
                |_hardcoded = [
                |    n for n in _ast.walk(_tree)
                |    if isinstance(n, _ast.Assign)
                |    and any(isinstance(t, _ast.Name) and t.id == 'primzahlen' for t in n.targets)
                |    and isinstance(n.value, _ast.List)
                |    and len(n.value.elts) >= 5
                |]
                |assert not _hardcoded
                |""".stripMargin,
            weight = 1.0,
            hint = Some("Compute the primes with a loop, do not hard-code the list literal."),
            hintDE = Some("Berechne die Primzahlen mit einer Schleife, nicht durch direktes Eintippen der Liste.")
          )
        ),
        fixtures = Nil,
        packages = Nil,
        timeoutMs = 5000,
        isScriptExercise = true
      )
    )
  // @end primesScript

  // @exercise val=wordCountScript id=script:word-count
  // ──────────────────────────────────────────────────────────────────────
  // Word Count Script  ·  script:word-count
  // ──────────────────────────────────────────────────────────────────────
  val wordCountScript: FeedbackExerciseDefinition =
    FeedbackExerciseDefinition(
      id = wordCountScriptExerciseId,
      titleTranslations = Map(
        english -> "Word count (Script)",
        german  -> "Wortanzahl (Script)"
      ),
      statementTranslations = Map(
        english -> ("Write a script that counts how often each word appears in " +
          "`text = \"die Katze sa\u00df auf der Matte die Katze sa\u00df\"` " +
          "and stores the result in a dictionary `wortanzahl` mapping each word to its count. " +
          "Words are separated by single spaces; keep the original casing."),
        german -> ("Schreibe ein Script, das z\u00e4hlt, wie oft jedes Wort in " +
          "`text = \"die Katze sa\u00df auf der Matte die Katze sa\u00df\"` vorkommt, " +
          "und das Ergebnis in einem Dictionary `wortanzahl` speichert, das jedes Wort auf seine Anzahl abbildet. " +
          "W\u00f6rter werden durch Leerzeichen getrennt; die Gro\u00df-/Kleinschreibung bleibt erhalten.")
      ),
      config = BlockFeedbackConfig(
        enableVmStaticChecks = true,
        enablePythonStaticChecks = true,
        enableUnitTests = true,
        enableAiSummary = true,
        visibleTests = Seq(
          BlockFeedbackPythonTest(
            name = "die_count",
            code = """assert wortanzahl["die"] == 2""",
            hint = Some("\"die\" appears twice in the text."),
            hintDE = Some("\"die\" kommt zweimal im Text vor.")
          ),
          BlockFeedbackPythonTest(
            name = "katze_count",
            code = """assert wortanzahl["Katze"] == 2""",
            hint = Some("\"Katze\" appears twice; keep the original casing."),
            hintDE = Some("\"Katze\" kommt zweimal vor; die Gro\u00df-/Kleinschreibung bleibt erhalten.")
          )
        ),
        hiddenTests = Seq(
          BlockFeedbackPythonTest(
            name = "unique_words",
            code = "assert len(wortanzahl) == 6",
            weight = 1.0,
            hint = Some("There are exactly 6 unique words in the text."),
            hintDE = Some("Es gibt genau 6 eindeutige W\u00f6rter im Text.")
          ),
          BlockFeedbackPythonTest(
            name = "total_count",
            code = "assert sum(wortanzahl.values()) == 9",
            weight = 2.0,
            hint = Some("The sum of all counts must equal the total number of words (9)."),
            hintDE = Some("Die Summe aller Z\u00e4hlwerte muss der Gesamtanzahl der W\u00f6rter (9) entsprechen.")
          ),
          BlockFeedbackPythonTest(
            name = "all_words_present",
            code = """assert all(w in wortanzahl for w in ["die", "Katze", "sa\u00df", "auf", "der", "Matte"])""",
            weight = 2.0,
            hint = Some("All words from the text must appear as keys in wortanzahl."),
            hintDE = Some("Alle W\u00f6rter aus dem Text m\u00fcssen als Schl\u00fcssel in wortanzahl vorkommen.")
          ),
          BlockFeedbackPythonTest(
            name = "single_counts",
            code = """assert wortanzahl["auf"] == 1 and wortanzahl["der"] == 1 and wortanzahl["Matte"] == 1""",
            weight = 1.0,
            hint = Some("\"auf\", \"der\", and \"Matte\" each appear exactly once."),
            hintDE = Some("\"auf\", \"der\" und \"Matte\" kommen jeweils genau einmal vor.")
          ),
          BlockFeedbackPythonTest(
            name = "no_hardcode",
            code =
              """import ast as _ast
                |_tree = _ast.parse(_student_source)
                |_hardcoded = [
                |    n for n in _ast.walk(_tree)
                |    if isinstance(n, _ast.Assign)
                |    and any(isinstance(t, _ast.Name) and t.id == 'wortanzahl' for t in n.targets)
                |    and isinstance(n.value, _ast.Dict)
                |    and len(n.value.keys) >= 3
                |]
                |assert not _hardcoded
                |""".stripMargin,
            weight = 1.0,
            hint = Some("Count the words with a loop, do not hard-code the dictionary literal."),
            hintDE = Some("Z\u00e4hle die W\u00f6rter mit einer Schleife, nicht durch direktes Eintippen des Dictionarys.")
          )
        ),
        fixtures = Nil,
        packages = Nil,
        timeoutMs = 4000,
        isScriptExercise = true
      )
    )
  // @end wordCountScript

  // @exercise val=flattenNested id=block:flatten-nested
  // ──────────────────────────────────────────────────────────────────────
  // Flatten a nested list  ·  block:flatten-nested
  // ──────────────────────────────────────────────────────────────────────
  val flattenNested: FeedbackExerciseDefinition =
    FeedbackExerciseDefinition(
      id = flattenNestedExerciseId,
      titleTranslations = Map(
        english -> "Flatten a nested list",
        german -> "Verschachtelte Liste glätten"
      ),
      statementTranslations = Map(
        english -> "Implement `flatten(xs)` that takes an arbitrarily nested list and returns a flat list of all the leaf numbers in order.\n\nExample: `flatten([1, [2, [3, 4]], 5])` → `[1, 2, 3, 4, 5]`",
        german -> "Implementiere `flatten(xs)`, das eine beliebig tief verschachtelte Liste entgegennimmt und eine flache Liste aller Zahlen in Reihenfolge zurückgibt.\n\nBeispiel: `flatten([1, [2, [3, 4]], 5])` → `[1, 2, 3, 4, 5]`"
      ),
      config =
        BlockFeedbackConfig(
          enableVmStaticChecks = true,
          enablePythonStaticChecks = true,
          enableUnitTests = true,
          enableAiSummary = true,
          visibleTests = Seq(
            BlockFeedbackPythonTest(
              name = "flat_input",
              code = "assert flatten([1, 2, 3]) == [1, 2, 3]",
              hint = Some("A list with no nesting should be returned unchanged."),
              hintDE = Some("Eine Liste ohne Verschachtelung soll unverändert zurückgegeben werden.")
            ),
            BlockFeedbackPythonTest(
              name = "one_level_nested",
              code = "assert flatten([1, [2, 3], 4]) == [1, 2, 3, 4]",
              hint = Some("Handle one level of nesting first."),
              hintDE = Some("Behandle zuerst eine Ebene der Verschachtelung.")
            ),
            BlockFeedbackPythonTest(
              name = "deeply_nested",
              code = "assert flatten([1, [2, [3, [4, [5]]]]]) == [1, 2, 3, 4, 5]",
              hint = Some("Think recursively: a nested list is itself a list that also needs flattening."),
              hintDE = Some("Denke rekursiv: Eine verschachtelte Liste ist selbst eine Liste, die auch geglättet werden muss.")
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "empty_list",
              code = "assert flatten([]) == []",
              hint = Some("An empty list should return an empty list."),
              hintDE = Some("Eine leere Liste soll eine leere Liste zurückgeben.")
            ),
            BlockFeedbackPythonTest(
              name = "empty_nested",
              code = "assert flatten([[], [[], []]]) == []",
              hint = Some("Empty sublists contribute nothing to the result."),
              hintDE = Some("Leere Unterlisten tragen nichts zum Ergebnis bei.")
            ),
            BlockFeedbackPythonTest(
              name = "mixed_depth",
              code = "assert flatten([[1, 2], 3, [4, [5, 6]], 7]) == [1, 2, 3, 4, 5, 6, 7]",
              weight = 2.0,
              hint = Some("Elements at different nesting depths must all appear in left-to-right order."),
              hintDE = Some("Elemente auf unterschiedlichen Verschachtelungsebenen müssen alle in Links-nach-rechts-Reihenfolge erscheinen.")
            ),
            BlockFeedbackPythonTest(
              name = "negative_and_zero",
              code = "assert flatten([[-1, 0], [1, [-2, 3]]]) == [-1, 0, 1, -2, 3]",
              hint = Some("Negative numbers and zero must be preserved."),
              hintDE = Some("Negative Zahlen und Null müssen erhalten bleiben.")
            ),
            BlockFeedbackPythonTest(
              name = "single_element",
              code = "assert flatten([[[[42]]]]) == [42]",
              weight = 1.5,
              hint = Some("A single number wrapped in multiple layers should still be unwrapped completely."),
              hintDE = Some("Eine einzelne Zahl in mehreren Schichten soll vollständig entschachtelt werden.")
            ),
            BlockFeedbackPythonTest(
              name = "order_preserved",
              code = "assert flatten([[3, 1], [4, 1], [5, 9], [2, 6]]) == [3, 1, 4, 1, 5, 9, 2, 6]",
              hint = Some("The original left-to-right order must be preserved exactly."),
              hintDE = Some("Die ursprüngliche Reihenfolge von links nach rechts muss exakt erhalten bleiben.")
            )
          ),
          fixtures = Nil,
          packages = Nil,
          timeoutMs = 4000
        )
    )
  // @end flattenNested

  // @exercise val=caesarCipher id=block:caesar-cipher
  // ──────────────────────────────────────────────────────────────────────
  // Caesar cipher  ·  block:caesar-cipher
  // ──────────────────────────────────────────────────────────────────────
  val caesarCipher: FeedbackExerciseDefinition =
    FeedbackExerciseDefinition(
      id = caesarCipherExerciseId,
      titleTranslations = Map(
        english -> "Caesar cipher",
        german -> "Caesar-Verschlüsselung"
      ),
      statementTranslations = Map(
        english -> "Implement `caesar(text, shift)` that encodes a string with a Caesar cipher: shift each letter by `shift` positions in the alphabet, wrapping around. Preserve case; leave non-letter characters unchanged.\n\nExample: `caesar('Hello, World!', 3)` → `'Khoor, Zruog!'`",
        german -> "Implementiere `caesar(text, shift)`, das einen String mit einer Caesar-Verschlüsselung kodiert: Verschiebe jeden Buchstaben um `shift` Positionen im Alphabet (mit Umbruch). Groß-/Kleinschreibung bleibt erhalten; Nicht-Buchstaben bleiben unverändert.\n\nBeispiel: `caesar('Hello, World!', 3)` → `'Khoor, Zruog!'`"
      ),
      config =
        BlockFeedbackConfig(
          enableVmStaticChecks = true,
          enablePythonStaticChecks = true,
          enableUnitTests = true,
          enableAiSummary = true,
          visibleTests = Seq(
            BlockFeedbackPythonTest(
              name = "classic_rot3",
              code = "assert caesar('Hello, World!', 3) == 'Khoor, Zruog!'",
              hint = Some("The classic Caesar shift is 3. Punctuation and spaces stay unchanged."),
              hintDE = Some("Die klassische Caesar-Verschiebung ist 3. Satzzeichen und Leerzeichen bleiben unverändert.")
            ),
            BlockFeedbackPythonTest(
              name = "lowercase",
              code = "assert caesar('abc', 1) == 'bcd'",
              hint = Some("Lowercase letters must also be shifted."),
              hintDE = Some("Kleinbuchstaben müssen ebenfalls verschoben werden.")
            ),
            BlockFeedbackPythonTest(
              name = "wrap_around",
              code = "assert caesar('xyz', 3) == 'abc'",
              hint = Some("After 'z' the alphabet wraps back to 'a'."),
              hintDE = Some("Nach 'z' beginnt das Alphabet wieder bei 'a'.")
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "uppercase_wrap",
              code = "assert caesar('XYZ', 3) == 'ABC'",
              hint = Some("Uppercase letters also wrap from Z back to A."),
              hintDE = Some("Großbuchstaben wechseln ebenfalls von Z zu A.")
            ),
            BlockFeedbackPythonTest(
              name = "mixed_case_preserved",
              code = "assert caesar('AbCdEf', 13) == 'NoPqRs'",
              weight = 2.0,
              hint = Some("The case of each letter must be preserved after shifting."),
              hintDE = Some("Die Groß-/Kleinschreibung jedes Buchstabens muss nach der Verschiebung erhalten bleiben.")
            ),
            BlockFeedbackPythonTest(
              name = "shift_zero",
              code = "assert caesar('Hello', 0) == 'Hello'",
              hint = Some("A shift of 0 must return the original string unchanged."),
              hintDE = Some("Eine Verschiebung von 0 muss den ursprünglichen String unverändert zurückgeben.")
            ),
            BlockFeedbackPythonTest(
              name = "shift_26_identity",
              code = "assert caesar('Python', 26) == 'Python'",
              hint = Some("Shifting by the full alphabet length (26) is the same as no shift."),
              hintDE = Some("Eine Verschiebung um die volle Alphabetlänge (26) entspricht keiner Verschiebung.")
            ),
            BlockFeedbackPythonTest(
              name = "non_letters_unchanged",
              code = "assert caesar('123 !@# abc', 5) == '123 !@# fgh'",
              hint = Some("Digits, spaces, and symbols must not be shifted."),
              hintDE = Some("Ziffern, Leerzeichen und Symbole dürfen nicht verschoben werden.")
            ),
            BlockFeedbackPythonTest(
              name = "large_shift",
              code = "assert caesar('abc', 29) == 'def'",
              hint = Some("Shifts larger than 26 should still work by wrapping modulo 26."),
              hintDE = Some("Verschiebungen größer als 26 sollen durch Modulo 26 korrekt funktionieren.")
            ),
            BlockFeedbackPythonTest(
              name = "negative_shift_decode",
              code = "assert caesar('Khoor', -3) == 'Hello'",
              weight = 2.0,
              hint = Some("A negative shift decodes a previously encoded message."),
              hintDE = Some("Eine negative Verschiebung dekodiert eine zuvor kodierte Nachricht.")
            ),
            BlockFeedbackPythonTest(
              name = "empty_string",
              code = "assert caesar('', 7) == ''",
              hint = Some("An empty string should return an empty string."),
              hintDE = Some("Ein leerer String soll einen leeren String zurückgeben.")
            ),
            BlockFeedbackPythonTest(
              name = "roundtrip",
              code = "assert caesar(caesar('Secret message 42!', 17), -17) == 'Secret message 42!'",
              weight = 2.0,
              hint = Some("Encoding and then decoding with the opposite shift must restore the original text."),
              hintDE = Some("Kodieren und anschließendes Dekodieren mit der entgegengesetzten Verschiebung muss den Originaltext wiederherstellen.")
            )
          ),
          fixtures = Nil,
          packages = Nil,
          timeoutMs = 4000
        )
    )
  // @end caesarCipher

  // @byExerciseId ────────────────────────────────────────────────────────
  // Lookup map and public API – add new exercises here after defining them.
  // ──────────────────────────────────────────────────────────────────────
  val byExerciseId: Map[String, FeedbackExerciseDefinition] =
    Map(
      addTwoNumbersExerciseId -> addTwoNumbers,
      maxInListExerciseId -> maxInList,
      balancedBracketsExerciseId -> balancedBrackets,
      twoSumIndicesExerciseId -> twoSumIndices,
      palindromeExerciseId -> palindrome,
      gcdExerciseId -> gcd,
      countVowelsExerciseId -> countVowels,
      runLengthEncodeExerciseId -> runLengthEncode,
      mergeSortedExerciseId -> mergeSorted,
      uniquePreserveOrderExerciseId -> uniquePreserveOrder,
      romanToIntExerciseId -> romanToInt,
      intToRomanExerciseId -> intToRoman,
      normalizeWhitespaceExerciseId -> normalizeWhitespace,
      rotateListExerciseId -> rotateList,
      // Script exercises
      fizzBuzzScriptExerciseId -> fizzBuzzScript,
      evenSquaresScriptExerciseId -> evenSquaresScript,
      fibonacciScriptExerciseId -> fibonacciScript,
      primesScriptExerciseId -> primesScript,
      wordCountScriptExerciseId -> wordCountScript,
      flattenNestedExerciseId -> flattenNested,
      caesarCipherExerciseId -> caesarCipher
    )

  /** Lookup an exercise definition by id. */
  def getExercise(exerciseId: String): Option[FeedbackExerciseDefinition] =
    byExerciseId.get(exerciseId)

  /** List all known feedback exercises (stable order). */
  def listExercises: Seq[FeedbackExerciseDefinition] =
    byExerciseId.values.toSeq.sortBy(_.id)
}
