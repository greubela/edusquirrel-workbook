package it.evadid.evacuation.io.instances


import it.evadid.evacuation.core.datastructures.seqs.BitSequence
import util.TestUtil

object TestBitSequence {



  def testPrePostFix(): Unit = {
    val seq = new BitSequence(List(true, false, false, true, true))
    assert(seq.hasPrefix(BitSequence(4)), "Seq hat Prefix 100")
    assert(!seq.hasPrefix(BitSequence(5)), "Seq has not Prefix 101")
    assert(seq.hasPostfix(BitSequence(3)), "Seq has Postfix 11")
    assert(!seq.hasPostfix(BitSequence(5)), "Seq has not Postfix 100")
    assert(!seq.hasPostfix(BitSequence(500)), "Seq is not long enough")
    assert(!seq.hasPrefix(BitSequence(500)), "Seq ist not long enough")
  }

  def testIntConversion(number: Int): Unit = {
    print("test: " + number)
    val bitSeq = BitSequence(number)
    print(" --> " + bitSeq)
    val back = bitSeq.toInt
    print(" --> " + back)
    assert(number == back, "Error in converting " + number + ": " + bitSeq + " ---> " + back)
    println(" [success]")
  }


  def main(args: Array[String]): Unit = {
    (TestUtil.classNumbers() ++ TestUtil.rndNumbers()).foreach(testIntConversion)
    testPrePostFix()
  }

}
