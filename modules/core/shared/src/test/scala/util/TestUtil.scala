package util

object TestUtil {
  def classNumbers(): List[Int] = List(0, 1, -1, 2, -2, 7, -7, 127, 128, 255, 256, 1024, Int.MaxValue, Int.MinValue)

  def rndNumbers(): List[Int] = {
    val random = new scala.util.Random(0)
    List.fill(128)(random.nextInt())
  }

  def repetitionList(value: Int = 7, size: Int = 1024, repeatedEvery: Int = 16, distinctModulo: Int = 251): List[Int] = {
    List.tabulate(size) { index =>
      if (index % repeatedEvery == 0) value else Math.floorMod(index, distinctModulo)
    }
  }
}
