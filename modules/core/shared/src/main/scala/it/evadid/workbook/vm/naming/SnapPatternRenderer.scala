package it.evadid.workbook.vm.naming

import it.evadid.workbook.vm.code.BeDefineStructure
import it.evadid.workbook.vm.code.defining.{BeDefineClass, BeDefineFunction}
import it.evadid.workbook.vm.code.usage.BeFunctionCall

object SnapPatternRenderer {


  def renderDefAsSnapPattern(structure: BeDefineStructure) = {
    structure.match {
      case s: BeDefineFunction => ???
      case _: BeDefineClass => ???
    }

  }

  def functionCallToSnapPattern(function: BeFunctionCall): String = {
    ???
  }


}
