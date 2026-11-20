package it.evadid.vm.editing

import it.evadid.core.datastructures.language.{AppLanguage, LanguageMap}
import it.evadid.core.datastructures.language.AppLanguage.HumanLanguage
import it.evadid.vm.code.abstractions.BeExpression
import it.evadid.vm.code.controlStructures.BeSequence
import it.evadid.vm.code.others.BeStartProgram
import it.evadid.vm.conversion.pythonexpression.PythonToExpression

/** A template that a block editor can copy into a program. */
final case class BeBlockTemplate(statement: BeExpression, label: Option[LanguageMap[HumanLanguage]] = None)

/** Statement templates offered by a block editor, built with the same parser as source programs
  * so that inserted blocks convert and execute like typed code.
  */
final case class BeBlockLibrary(templates: Seq[BeBlockTemplate])

object BeBlockLibrary {

  /** The first statement of a one-statement Python program. */
  def statementFromPython(source: String): BeExpression =
    PythonToExpression.program(source).fullProgram match {
      case BeStartProgram(Some(BeSequence(statement +: _, _))) => statement
      case other => throw IllegalArgumentException(s"Expected one statement in '$source', got $other.")
    }

  private def template(source: String, label: Option[LanguageMap[HumanLanguage]] = None): BeBlockTemplate =
    BeBlockTemplate(statementFromPython(source), label)

  private def label(english: String, german: String, french: String, ukrainian: String, russian: String,
                    turkish: String, danish: String, spanish: String): Option[LanguageMap[HumanLanguage]] =
    Some(LanguageMap.mapBasedLanguageMap[HumanLanguage](Map(AppLanguage.English -> english, AppLanguage.German -> german,
      AppLanguage.French -> french, AppLanguage.Ukrainian -> ukrainian, AppLanguage.Russian -> russian,
      AppLanguage.Turkish -> turkish, AppLanguage.Danish -> danish, AppLanguage.Spanish -> spanish)))

  /** Turtle motion and pen commands with labelled repetition and branching. */
  lazy val turtle: BeBlockLibrary = BeBlockLibrary(
    List("forward(100)", "backward(100)", "left(90)", "right(90)", "penup()", "pendown()").map(template(_)) ++ List(
      template("for _ in range(4):\n    pass", label("REPEAT-TIMES", "WIEDERHOLE-MAL", "RÉPÉTER-FOIS", "ПОВТОРИТИ-РАЗІВ",
        "ПОВТОРИТЬ-РАЗ", "TEKRARLA-KEZ", "GENTAG-GANGE", "REPETIR-VECES")),
      template("while True:\n    pass", label("REPEAT-WHILE", "WIEDERHOLE-SOLANGE", "RÉPÉTER-TANT-QUE", "ПОВТОРЮВАТИ-ПОКИ",
        "ПОВТОРЯТЬ-ПОКА", "TEKRARLA-SÜRECE", "GENTAG-MENS", "REPETIR-MIENTRAS")),
      template("if True:\n    pass\nelse:\n    pass", label("IF/THEN/ELSE", "FALLS/DANN/SONST", "SI/ALORS/SINON",
        "ЯКЩО/ТОДІ/ІНАКШЕ", "ЕСЛИ/ТОГДА/ИНАЧЕ", "EĞER/O HALDE/AKSİ", "HVIS/SÅ/ELLERS", "SI/ENTONCES/SINO"))))
}
