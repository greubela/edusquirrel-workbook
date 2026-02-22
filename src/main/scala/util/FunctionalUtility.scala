package util

import com.raquo.laminar.api.L.Var

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object FunctionalUtility {

  def withCacheToVar[I, O](operation: I => Future[O]): I => Var[Option[O]] = {

    val cachedResults: mutable.Map[I, O] = mutable.Map[I, O]()

    def getOutput(input: I): Var[Option[O]] = {

      if (cachedResults.contains(input)) Var(Some(cachedResults(input)))
      else {
        val resVar = Var[Option[O]](None)
        val futO = operation(input)
        futO.onComplete {
          case Success(output) => {
            println("successfully calculated: " + output)
            cachedResults.put(input, output)
            resVar.set(Some(output))
          }
          case Failure(error) => println("[ERROR] at calculation given to withCacheToVar: " + error.printStackTrace())
        }(ExecutionContext.global)
        resVar
      }
    }

    getOutput
  }

  def withCache[I, O](function: I => O): I => O = {

    val cachedResults: mutable.Map[I, O] = mutable.Map[I, O]()

    def getCachedOrCalculate(input: I): O = {
      if (cachedResults.contains(input)) {
        cachedResults(input)
      } else {
        function(input)
      }
    }

    getCachedOrCalculate
  }

  def withCacheAndResolvedDependencies[I, O](functionWithDependencies: (I, I => O) => O): I => O = {

    val startedCalc: mutable.Stack[I] = mutable.Stack[I]()
    val cachedResults: mutable.Map[I, O] = mutable.Map[I, O]()

    def getCachedOrCalculate(input: I): O = {
      if (cachedResults.contains(input)) {
        cachedResults(input)
      }
      else if (startedCalc.contains(input)) {
        throw new IllegalStateException("Cyclic dependency in tree calculation!\n    " + startedCalc.mkString("    ", "    \n", "\n") + "but now calling: \n    " + input)
      } else {
        startedCalc.push(input)
        val result = functionWithDependencies(input, element => getCachedOrCalculate(element))
        cachedResults.put(input, result)
        val popped = startedCalc.pop()
        assert(popped == input, "wierd behavior: popped a different element than the calculation pushed?")
        result
      }
    }

    getCachedOrCalculate
  }

}
