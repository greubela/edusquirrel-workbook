package util

import scala.collection.mutable

object FunctionalUtility {

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
