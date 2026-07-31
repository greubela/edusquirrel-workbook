package it.evadid.vm.naming

import it.evadid.vm.code.abstractions.BeDefineStructure
import it.evadid.vm.code.defining.{BeDefineClass, BeDefineFunction}
import it.evadid.vm.code.usage.BeFunctionCall

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
