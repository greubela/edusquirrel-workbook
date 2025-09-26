package interactionPlugins.pythonExercises

import contentmanagement.model.language.AppLanguage
import workbook.model.exercise.ExerciseContent

final case class PythonExerciseContent(
    id: String,
    titleTranslations: Map[AppLanguage, String],
    instructionTranslations: Map[AppLanguage, String],
    estimatedTimeInMinutes: Double,
    starterCode: String,
    visibleTests: Seq[PythonUnitTest],
    hiddenTests: Seq[PythonUnitTest],
    packages: Seq[String] = Nil,
    fixtures: Seq[PythonFixture] = Nil,
    timeoutMs: Int = 5000,
    memoryLimitMb: Int = 128
) extends ExerciseContent {

  override def titleMap: Map[AppLanguage, String] = titleTranslations

  override def instructionMap: Map[AppLanguage, String] = instructionTranslations

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

object PythonExerciseContent {

  private val english = AppLanguage.English

  val helloWorld: PythonExerciseContent = PythonExerciseContent(
    id = "python-hello-world",
    titleTranslations = Map(english -> "Hello World"),
    instructionTranslations = Map(
      english -> "Write a function named `say_hello` that returns the string `Hello, World!` and prints it when executed."
    ),
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

  val fizzBuzz: PythonExerciseContent = PythonExerciseContent(
    id = "python-fizzbuzz",
    titleTranslations = Map(english -> "FizzBuzz"),
    instructionTranslations = Map(
      english -> "Implement `fizzbuzz_sequence(limit)` returning a list from 1..limit with FizzBuzz substitutions."
    ),
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

  val ninetyNineBottles: PythonExerciseContent = PythonExerciseContent(
    id = "python-99-bottles",
    titleTranslations = Map(english -> "99 Bottles of Beer"),
    instructionTranslations = Map(
      english -> "Create `verse(start)` that returns the lyrics for a single verse of '99 Bottles of Beer'."
    ),
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

  val fibonacci: PythonExerciseContent = PythonExerciseContent(
    id = "python-fibonacci",
    titleTranslations = Map(english -> "Fibonacci Sequence"),
    instructionTranslations = Map(
      english -> "Implement `fibonacci(n)` returning a list with the first n Fibonacci numbers starting at 0."
    ),
    estimatedTimeInMinutes = 6,
    starterCode =
      """|def fibonacci(n: int) -> list[int]:
         |    #Return the first n Fibonacci numbers.
         |    if n <= 0:
         |        return []
         |    if n == 1:
         |        return [0]
         |    sequence = [0, 1]
         |    while len(sequence) < n:
         |        sequence.append(0)
         |    return sequence
         |""".stripMargin,
    visibleTests = Seq(
      PythonUnitTest(
        name = "First numbers",
        code =
          """from student_solution import fibonacci

assert fibonacci(1) == [0]
assert fibonacci(2) == [0, 1]
"""",
        hint = Some("Each number after the first two is the sum of the previous two numbers.")
      ),
      PythonUnitTest(
        name = "Longer prefix",
        code =
          """from student_solution import fibonacci

assert fibonacci(7) == [0, 1, 1, 2, 3, 5, 8]
""""
      )
    ),
    hiddenTests = Seq(
      PythonUnitTest(
        name = "Handles zero",
        code =
          """from student_solution import fibonacci

assert fibonacci(0) == []
assert fibonacci(10)[-1] == 34
""""
      )
    )
  )

  val openLibraryRequest: PythonExerciseContent = PythonExerciseContent(
    id = "python-openlibrary-request",
    titleTranslations = Map(english -> "Open Library Lookup"),
    instructionTranslations = Map(
      english -> "Implement `fetch_openlibrary()` that uses requests to fetch the book JSON and returns the response text."
    ),
    estimatedTimeInMinutes = 10,
    starterCode =
      """|def fetch_openlibrary() -> str:
         |    #Fetch the Open Library JSON for OL37397230M and return the response text.
         |    raise NotImplementedError("Use the requests library to retrieve the JSON and return its text.")
         |""".stripMargin,
    visibleTests = Seq(
      PythonUnitTest(
        name = "Response contains key",
        code =
          """import pyodide_http
pyodide_http.patch_all()

from student_solution import fetch_openlibrary

data = fetch_openlibrary()
assert "OL37397230M" in data, "The response should mention the requested book identifier."
"""",
        hint = Some("Call requests.get with the provided URL and return the `.text` of the response.")
      )
    ),
    hiddenTests = Seq(
      PythonUnitTest(
        name = "Response parses as JSON",
        code =
          """import json
import pyodide_http
pyodide_http.patch_all()

from student_solution import fetch_openlibrary

payload = fetch_openlibrary()
parsed = json.loads(payload)
assert parsed["key"] == "/books/OL37397230M", "The JSON should contain the book key."
assert "title" in parsed, "The book metadata should include a title."
""""
      )
    ),
    packages = Seq("pyodide-http", "requests"),
    timeoutMs = 10000
  )

  val defaults: Seq[PythonExerciseContent] = Seq(
    helloWorld,
    fizzBuzz,
    fibonacci,
    ninetyNineBottles,
    openLibraryRequest
  )
}
