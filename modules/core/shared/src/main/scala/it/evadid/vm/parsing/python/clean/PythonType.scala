package it.evadid.vm.parsing.python.clean

import fastparse.{P, *}
import it.evadid.vm.parsing.generic.CodeLexer.*
import fastparse.*
import it.evadid.vm.parsing.generic.CodeLexer.*
import it.evadid.vm.parsing.python.clean.PyAST.*
import it.evadid.vm.parsing.python.clean.Python313Parser.*

trait PythonType(val pythonRepresentation: String) {

}

object PythonType {

  sealed trait Numeric extends PythonType

  case object PYTHON_INTEGER extends PythonType("int") with Numeric

  case object PYTHON_FLOAT extends PythonType("float") with Numeric

  case object PYTHON_COMPLEX extends PythonType("complex") with Numeric

  case object PYTHON_STRING extends PythonType("str")

  case object PYTHON_BOOL extends PythonType("bool")

  case object PYTHON_NONE extends PythonType("NoneType")

  case class PYTHON_UNION_TYPE(a: PythonType, b: PythonType) extends PythonType(a.pythonRepresentation + "|" + b.pythonRepresentation)

  case class PYTHON_OPTIONAL(a: PythonType) extends PythonType(a.pythonRepresentation + "|None")

  case object PYTHON_ANY extends PythonType("Any")

  case object PYTHON_FUNCTION extends PythonType("function")

  case class PYTHON_UNPARSABLE_TYPE(str: String) extends PythonType(str)

//  val allAtomicTypes: List[PythonType] = List(PYTHON_INTEGER, PYTHON_FLOAT, PYTHON_COMPLEX, PYTHON_STRING, PYTHON_BOOL, PYTHON_NONE, PYTHON_ANY, PYTHON_FUNCTION)


  def atomic_type[ctx: P]: P[PythonType] =
    P("bool").!.map(_ => PYTHON_BOOL)
      | P("Any").!.map(_ => PYTHON_ANY)
      | P("function").!.map(_ => PYTHON_FUNCTION)
      | P("str").!.map(_ => PYTHON_STRING)
      | P("int").!.map(_ => PYTHON_INTEGER)
      | P("float").!.map(_ => PYTHON_FLOAT)
      | NAME.map(str => PYTHON_UNPARSABLE_TYPE(str))

  def expression_type[ctx: P]: P[PythonType] = {
    (atomic_type  ~~ SPACES.? ~~ VBAR ~~ SPACES.? ~~ expression_type).map { case (t1, t2) => PYTHON_UNION_TYPE(t1, t2) }
    | atomic_type
  }


}
