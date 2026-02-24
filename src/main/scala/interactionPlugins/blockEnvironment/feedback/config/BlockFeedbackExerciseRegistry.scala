package interactionPlugins.blockEnvironment.feedback

import contentmanagement.model.language.AppLanguage

/**
 * In-memory registry of feedback exercise definitions.
 *
 * This keeps per-exercise feedback configuration close to the exercise id and
 * optionally includes the task statement translations.
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
              weight = 2.0
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
              code = "res = two_sum_indices([10, 22, 5, 7, 19, 3], 29)\nassert res in [(0, 4), (1, 3)]",
              weight = 2.0
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
              hint = Some("Denk an Groß-/Kleinschreibung.")
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "ignore_punct",
              code = "assert is_palindrome('A man, a plan, a canal: Panama!') == True",
              weight = 2.0,
              hint = Some("Ignoriere Nicht-Buchstaben/Ziffern.")
            ),
            BlockFeedbackPythonTest(
              name = "digits_mix",
              code = "assert is_palindrome('12-21') == True",
              weight = 2.0
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
              hint = Some("Definiere gcd(0, n) sinnvoll.")
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "negative_inputs",
              code = "assert gcd(-12, 18) == 6",
              weight = 2.0,
              hint = Some("Achte auf negatives Vorzeichen.")
            ),
            BlockFeedbackPythonTest(
              name = "both_zero",
              code = "assert gcd(0, 0) == 0",
              weight = 2.0
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
              hint = Some("Case-insensitive zählen.")
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "spaces_and_punct",
              code = "assert count_vowels('A, E! I? O. U') == 5",
              weight = 2.0
            ),
            BlockFeedbackPythonTest(
              name = "no_vowels",
              code = "assert count_vowels('rhythms') == 0",
              weight = 2.0
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
              hint = Some("Denk an Wechsel zwischen Zeichen.")
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "with_spaces",
              code = "assert rle_encode('  !!') == [(' ', 2), ('!', 2)]",
              weight = 2.0
            ),
            BlockFeedbackPythonTest(
              name = "long_run",
              code = "assert rle_encode('zzzzzzzzzz') == [('z', 10)]",
              weight = 2.0
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
              hint = Some("Du darfst Duplikate nicht verlieren.")
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "one_empty",
              code = "assert merge_sorted([], [1, 2, 3]) == [1, 2, 3]",
              weight = 2.0
            ),
            BlockFeedbackPythonTest(
              name = "negatives",
              code = "assert merge_sorted([-5, -1, 0], [-6, -2, 3]) == [-6, -5, -2, -1, 0, 3]",
              weight = 2.0
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
              hint = Some("Reihenfolge der ersten Vorkommen behalten.")
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "already_unique",
              code = "assert unique([5, 4, 3]) == [5, 4, 3]",
              weight = 2.0
            ),
            BlockFeedbackPythonTest(
              name = "with_zero",
              code = "assert unique([0, 0, 0, 1, 0]) == [0, 1]",
              weight = 2.0
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
              hint = Some("Achte auf Subtraktionsregeln wie IV, IX, XL, ...")
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
              weight = 2.0
            ),
            BlockFeedbackPythonTest(
              name = "many",
              code = "assert roman_to_int('CDXLIV') == 444",
              weight = 2.0
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
              hint = Some("Nutze die Subtraktionsschreibweise (IV, IX, XL, ...).")
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
              weight = 2.0
            ),
            BlockFeedbackPythonTest(
              name = "444",
              code = "assert int_to_roman(444) == 'CDXLIV'",
              weight = 2.0
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
              hint = Some("Alle Whitespace-Arten zählen (Tab, Newline, ...).")
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "only_space",
              code = "assert normalize_whitespace('    ') == ''",
              weight = 2.0
            ),
            BlockFeedbackPythonTest(
              name = "mixed_unicode_space",
              code = "assert normalize_whitespace('x\\n\\n\\ty') == 'x y'",
              weight = 2.0
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
              hint = Some("Nutze k % len(xs).")
            )
          ),
          hiddenTests = Seq(
            BlockFeedbackPythonTest(
              name = "empty",
              code = "assert rotate([], 3) == []",
              weight = 2.0,
              hint = Some("Leere Liste ist Spezialfall.")
            ),
            BlockFeedbackPythonTest(
              name = "single",
              code = "assert rotate([42], 999) == [42]",
              weight = 2.0
            )
          ),
          fixtures = Nil,
          packages = Nil,
          timeoutMs = 5000
        )
    )
  // @end rotateList

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
      rotateListExerciseId -> rotateList
    )

  /** Lookup an exercise definition by id. */
  def getExercise(exerciseId: String): Option[FeedbackExerciseDefinition] =
    byExerciseId.get(exerciseId)

  /** List all known feedback exercises (stable order). */
  def listExercises: Seq[FeedbackExerciseDefinition] =
    byExerciseId.values.toSeq.sortBy(_.id)
}
