package it.evadid.evacuation.css

import it.evadid.evacuation.core.datastructures.utility.ObservableVar
import org.scalajs.dom.document

object CssHelper {


  def initCssUpdateRoutine(variables: Map[String, ObservableVar[?]]): Unit ={
    def updateVarsToCSS(): Unit = {
      document.body.style = variables.toList.map(tup => "--" + tup._1 + ": " + tup._2.currentValue + ";").mkString("\n")
    }

    variables.values.foreach(curVar => curVar.addListener((a, b) => updateVarsToCSS()))
  }

}
