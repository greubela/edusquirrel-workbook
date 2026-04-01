package interactionPlugins.programmingExercise.pythonExercisesUnsorted

import datastructures.core.language.{AppLanguage, HumanLanguage, LanguageMap}

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
  private val german = AppLanguage.German
  private val french = AppLanguage.French
  private val ukrainian = AppLanguage.Ukrainian
  private val russian = AppLanguage.Russian
  private val turkish = AppLanguage.Turkish
  private val danish = AppLanguage.Danish

  val helloWorld: PythonExerciseDescription = PythonExerciseDescription(
    id = "python-hello-world",
    titleTranslations = LanguageMap.mapBasedLanguageMap(Map(
      english -> "Hello World",
      german -> "Hallo Welt",
      french -> "Bonjour le monde",
      ukrainian -> "Привіт, світе",
      russian -> "Привет, мир",
      turkish -> "Merhaba Dünya",
      danish -> "Hej verden"
    )),
    instructionTranslations = LanguageMap.mapBasedLanguageMap(Map(
      english -> "Write a function named `say_hello` that returns the string `Hello, World!` and prints it when executed.",
      german -> "Schreibe eine Funktion mit dem Namen `say_hello`, die den String `Hello, World!` zurückgibt und ihn bei der Ausführung ausgibt.",
      french -> "Écris une fonction nommée `say_hello` qui renvoie la chaîne `Hello, World!` et l'affiche lors de l'exécution.",
      ukrainian -> "Напиши функцію з назвою `say_hello`, яка повертає рядок `Hello, World!` і виводить його під час виконання.",
      russian -> "Напиши функцию с именем `say_hello`, которая возвращает строку `Hello, World!` и выводит её при выполнении.",
      turkish -> "Çalıştırıldığında `Hello, World!` dizesini döndüren ve yazdıran `say_hello` adlı bir fonksiyon yaz.",
      danish -> "Skriv en funktion ved navn `say_hello`, der returnerer strengen `Hello, World!` og udskriver den ved kørsel."
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
    titleTranslations = LanguageMap.mapBasedLanguageMap(Map(
      english -> "FizzBuzz",
      german -> "FizzBuzz",
      french -> "FizzBuzz",
      ukrainian -> "FizzBuzz",
      russian -> "FizzBuzz",
      turkish -> "FizzBuzz",
      danish -> "FizzBuzz"
    )),
    instructionTranslations = LanguageMap.mapBasedLanguageMap(Map(
      english -> "Implement `fizzbuzz_sequence(limit)` returning a list from 1..limit with FizzBuzz substitutions.",
      german -> "Implementiere `fizzbuzz_sequence(limit)`, das eine Liste von 1..limit mit FizzBuzz-Ersetzungen zurückgibt.",
      french -> "Implémente `fizzbuzz_sequence(limit)` qui renvoie une liste de 1..limit avec les remplacements FizzBuzz.",
      ukrainian -> "Реалізуй `fizzbuzz_sequence(limit)`, що повертає список від 1..limit із підстановками FizzBuzz.",
      russian -> "Реализуй `fizzbuzz_sequence(limit)`, которая возвращает список от 1..limit с подстановками FizzBuzz.",
      turkish -> "1..limit aralığını FizzBuzz dönüşümleriyle liste olarak döndüren `fizzbuzz_sequence(limit)` fonksiyonunu uygula.",
      danish -> "Implementér `fizzbuzz_sequence(limit)`, som returnerer en liste fra 1..limit med FizzBuzz-erstatninger."
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
    titleTranslations = LanguageMap.mapBasedLanguageMap(Map(
      english -> "99 Bottles of Beer",
      german -> "99 Bottles of Beer",
      french -> "99 bouteilles de bière",
      ukrainian -> "99 пляшок пива",
      russian -> "99 бутылок пива",
      turkish -> "99 Şişe Bira",
      danish -> "99 flasker øl"
    )),
    instructionTranslations = LanguageMap.mapBasedLanguageMap(Map(
      english -> "Create `verse(start)` that returns the lyrics for a single verse of '99 Bottles of Beer'.",
      german -> "Erstelle `verse(start)`, das den Liedtext für eine einzelne Strophe von '99 Bottles of Beer' zurückgibt.",
      french -> "Crée `verse(start)` qui renvoie les paroles d'un seul couplet de '99 Bottles of Beer'.",
      ukrainian -> "Створи `verse(start)`, що повертає текст одного куплету пісні '99 Bottles of Beer'.",
      russian -> "Создай `verse(start)`, которая возвращает текст одного куплета песни '99 Bottles of Beer'.",
      turkish -> "'99 Bottles of Beer' şarkısının tek bir kıtasının sözlerini döndüren `verse(start)` fonksiyonunu oluştur.",
      danish -> "Lav `verse(start)`, der returnerer sangteksten for ét vers af '99 Bottles of Beer'."
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
