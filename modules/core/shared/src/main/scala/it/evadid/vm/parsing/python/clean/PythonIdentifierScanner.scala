package it.evadid.vm.parsing.python.clean

import it.evadid.vm.parsing.generic.abstractions.CodeIdentifierScanResult
import it.evadid.vm.parsing.generic.abstractions.pipeline.IdentifierScanner

object PythonIdentifierScanner extends IdentifierScanner[?, ?, ?, ?, ?]{



  def scanPythonCodeForIdentifier(pythonString: String): CodeIdentifierScanResult[?, ?, ?, ?] = ???


  val exampleToScan =
    """
      |import turtle
      |
      |x = 50
      |y: str = "30"
      |turtle.forward(100+50)
      |turtle.left(120)
      |turtle.forward(x - int(y))
      |turtle.right(50)
      |
      |""".stripMargin
  /*
      expected result:
      - [turtle] as import,
      - no classes
      - [+, -, int] as functions,
      - [(x, None, Some(50)), (y, Some(str), Some("30")] as variables
      */


}
