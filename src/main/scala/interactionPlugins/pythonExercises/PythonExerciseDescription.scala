package interactionPlugins.pythonExercises

import contentmanagement.model.language.*

final case class PythonExerciseDescription(
    id: String,
    titleTranslations: LanguageMap[HumanLanguage],
    instructionTranslations: LanguageMap[HumanLanguage],
    estimatedTimeInMinutes: Double,
    starterCode: String,
    visibleTests: Seq[PythonUnitTest],
    hiddenTests: Seq[PythonUnitTest],
    packages: Seq[String] = Nil,
    fixtures: Seq[PythonFixture] = Nil,
    timeoutMs: Int = 5000,
    memoryLimitMb: Int = 128
)  {

  def titleMap: LanguageMap[HumanLanguage] = titleTranslations

  def instructionMap: LanguageMap[HumanLanguage] = instructionTranslations

}

final case class PythonUnitTest(
    name: String,
    code: String,
    weight: Double = 1.0,
    hint: Option[String] = None
)

final case class PythonFixture(
    path: String,
    content: String,
    isBinary: Boolean = false
)

object PythonExerciseDescription {

  private val english = AppLanguage.English

  val helloWorld: PythonExerciseDescription = PythonExerciseDescription(
    id = "python-hello-world",
    titleTranslations = LanguageMap.mapBasedLanguageMap(Map(english -> "Hello World")),
    instructionTranslations = LanguageMap.mapBasedLanguageMap(Map(
      english -> "Write a function named `say_hello` that returns the string `Hello, World!` and prints it when executed."
    )),
    estimatedTimeInMinutes = 2,
    starterCode =
      """|def say_hello():
         |    #Return the classic greeting.
         |    message = "Hello, World!"
         |    print(message)
         |    return message
         |""".stripMargin,
    visibleTests = Seq(
      PythonUnitTest(
        name = "Greeting is returned",
        code =
          """from student_solution import say_hello

result = say_hello()
assert result == "Hello, World!", "The function should return the string 'Hello, World!'"
"""",
        hint = Some("Return the string exactly as 'Hello, World!'.")
      ),
      PythonUnitTest(
        name = "Greeting is printed",
        code =
          """import io
import sys

from student_solution import say_hello

buffer = io.StringIO()
old_stdout = sys.stdout
try:
    sys.stdout = buffer
    say_hello()
finally:
    sys.stdout = old_stdout

output = buffer.getvalue().strip()
assert output == "Hello, World!", "The function should print 'Hello, World!'"
""""
      )
    ),
    hiddenTests = Seq(
      PythonUnitTest(
        name = "No extra whitespace",
        code =
          """from student_solution import say_hello

result = say_hello()
assert result.strip() == "Hello, World!", "Ensure no additional whitespace is returned."
""""
      )
    )
  )

  val fizzBuzz: PythonExerciseDescription = PythonExerciseDescription(
    id = "python-fizzbuzz",
    titleTranslations = LanguageMap.mapBasedLanguageMap(Map(english -> "FizzBuzz")),
    instructionTranslations = LanguageMap.mapBasedLanguageMap(Map(
      english -> "Implement `fizzbuzz_sequence(limit)` returning a list from 1..limit with FizzBuzz substitutions."
    )),
    estimatedTimeInMinutes = 5,
    starterCode =
      """|def fizzbuzz_sequence(limit: int) -> list[str]:
         |    #Return the FizzBuzz sequence from 1 to limit inclusive.
         |    if limit < 1:
         |        return []
         |    sequence: list[str] = []
         |    for number in range(1, limit + 1):
         |        sequence.append(str(number))
         |    return sequence
         |""".stripMargin,
    visibleTests = Seq(
      PythonUnitTest(
        name = "Handles simple range",
        code =
          """from student_solution import fizzbuzz_sequence

expected = ["1", "2", "Fizz", "4", "Buzz"]
assert fizzbuzz_sequence(5) == expected, f"Expected {expected}"
"""",
        hint = Some("Replace multiples of 3 with 'Fizz', multiples of 5 with 'Buzz', and both with 'FizzBuzz'.")
      ),
      PythonUnitTest(
        name = "Handles combined multiples",
        code =
          """from student_solution import fizzbuzz_sequence

sequence = fizzbuzz_sequence(15)
assert sequence[14] == "FizzBuzz", "15 should map to 'FizzBuzz'"
""""
      )
    ),
    hiddenTests = Seq(
      PythonUnitTest(
        name = "Handles longer ranges",
        code =
          """from student_solution import fizzbuzz_sequence

result = fizzbuzz_sequence(30)
assert result.count("FizzBuzz") == 2, "Two numbers <= 30 should be 'FizzBuzz'"
assert result[10] == "Fizz", "11th element (value 11) should be 'Fizz'"
""""
      )
    )
  )

  val ninetyNineBottles: PythonExerciseDescription = PythonExerciseDescription(
    id = "python-99-bottles",
    titleTranslations = LanguageMap.mapBasedLanguageMap(Map(english -> "99 Bottles of Beer")),
    instructionTranslations = LanguageMap.mapBasedLanguageMap(Map(
      english -> "Create `verse(start)` that returns the lyrics for a single verse of '99 Bottles of Beer'."
    )),
    estimatedTimeInMinutes = 8,
    starterCode =
      """|def verse(start: int) -> str:
         |    #Return the verse for the provided bottle count.
         |    raise NotImplementedError("Implement the song verse generation here.")
         |""".stripMargin,
    visibleTests = Seq(
      PythonUnitTest(
        name = "Standard verse",
        code =
          """from student_solution import verse

expected = (
    "99 bottles of beer on the wall, 99 bottles of beer.\n"
    "Take one down and pass it around, 98 bottles of beer on the wall."
)
assert verse(99) == expected
"""",
        hint = Some("Follow the traditional lyrics including punctuation and casing.")
      ),
      PythonUnitTest(
        name = "Singular bottle",
        code =
          """from student_solution import verse

expected = (
    "1 bottle of beer on the wall, 1 bottle of beer.\n"
    "Take it down and pass it around, no more bottles of beer on the wall."
)
assert verse(1) == expected
""""
      )
    ),
    hiddenTests = Seq(
      PythonUnitTest(
        name = "No more bottles",
        code =
          """from student_solution import verse

expected = (
    "No more bottles of beer on the wall, no more bottles of beer.\n"
    "Go to the store and buy some more, 99 bottles of beer on the wall."
)
assert verse(0) == expected
""""
      )
    )
  )


  val defaults: Seq[PythonExerciseDescription] = Seq(
    helloWorld,
    fizzBuzz,
    ninetyNineBottles,
  )
}
