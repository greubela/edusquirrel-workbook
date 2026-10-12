package it.evadid.workbook.elements.interactionElements.blockchain

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.distribution.command.SerializedException
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import it.evadid.workbook.model.blockchain.*
import munit.FunSuite
import upickle.default.*

class TeachingChainSpec extends FunSuite {
  private val mined = TeachingChain(List(TeachingBlock("first", 1), TeachingBlock("second", 11), TeachingBlock("third", 25)))
  test("independent reference hashes verify the explicit header format and predecessor linkage") {
    assertEquals(mined.hashes, List(
      "09e056be6ef132352b16a84a95c1ebfba6e18bc91383a215fc85b38ca568ec56",
      "08860443967e45f046e1311528cec0834b3c0d4f880399c4d73471ce44ea643a",
      "0c57734e557ea33f8e28a80cef774a282e37b57917c82ad1a800a43075d3928d"))
    assertEquals(mined.previousHash(0), "0" * 64)
    assertEquals(mined.previousHash(2), mined.hashes(1))
    assertEquals(mined.validPrefixLength(1), 3)
    assertEquals(mined.validPrefixLength(2), 0)
  }
  test("payload length counts UTF-8 bytes and preserves whitespace") {
    val block = TeachingBlock("Grüße 🌍\n", 7)
    assertEquals(block.hash(0, TeachingChain.genesisPreviousHash), "ec5f7d980e7eca9ffded0d8b658f0e3e8bd1505f48f1c2e9b89a752a07e6a9ca")
    assertNotEquals(block.hash(0, TeachingChain.genesisPreviousHash), block.copy(data = block.data.trim).hash(0, TeachingChain.genesisPreviousHash))
    assertNotEquals(block.hash(0, TeachingChain.genesisPreviousHash), block.hash(1, TeachingChain.genesisPreviousHash))
  }
  test("editing data or a nonce affects that block and successors, leaving predecessors untouched") {
    for (block <- List(mined.blocks(1).copy(data = "tampered"), mined.blocks(1).copy(nonce = 12))) {
      val changed = mined.update(1, block)
      assertEquals(changed.hashes.head, mined.hashes.head)
      assertNotEquals(changed.hashes(1), mined.hashes(1))
      assertNotEquals(changed.hashes(2), mined.hashes(2))
      assertEquals(changed.previousHash(2), changed.hashes(1))
    }
    assertEquals(mined.blocks(1), TeachingBlock("second", 11))
  }
  test("later proof alone cannot validate a prefix with an unmined predecessor") {
    val changed = mined.update(0, TeachingBlock("changed", 0))
    val result = changed.mine(1, 1, 0, 1000)
    assert(result.found)
    val laterMined = changed.update(1, changed.blocks(1).copy(nonce = result.nonce))
    assert(laterMined.proofs(1)(1))
    assertEquals(laterMined.validPrefixLength(1), 0)
  }
  test("bounded mining finds the first valid nonce and can rebuild a tampered chain in order") {
    val first = TeachingChain(List(TeachingBlock("first")))
    assertEquals(first.mine(0, 1, 0, 10), MiningResult(1, 2, true))
    var chain = mined.update(0, TeachingBlock("changed", 0))
    for (index <- chain.blocks.indices) {
      val result = chain.mine(index, 1, 0, 1000)
      assert(result.found)
      chain = chain.update(index, chain.blocks(index).copy(nonce = result.nonce))
    }
    assertEquals(chain.validPrefixLength(1), 3)
  }
  test("a failed batch can resume and never wraps the maximum nonce") {
    val chain = TeachingChain(List(TeachingBlock("first")))
    assertEquals(chain.mine(0, 1, 0, 1), MiningResult(0, 1, false))
    assertEquals(chain.mine(0, 1, 1, 1), MiningResult(1, 1, true))
    val last = chain.mine(0, 4, Int.MaxValue, 1000)
    assertEquals(last.nonce, Int.MaxValue)
    assertEquals(last.attempts, 1)
    assertEquals(last.nextNonce, None)
  }
  test("nonces and definitions reject unsupported values and oversized histories") {
    for (raw <- List("", " ", "-1", "1.2", "1e3", "NaN", "2147483648", "１２", "0" * 11))
      assertEquals(TeachingChain.parseNonce(raw), None)
    assertEquals(TeachingChain.parseNonce("0001"), Some(1))
    assertEquals(TeachingChain.parseNonce(Int.MaxValue.toString), Some(Int.MaxValue))
    intercept[IllegalArgumentException](TeachingBlock("x", -1))
    intercept[IllegalArgumentException](TeachingBlock("x" * 4097))
    intercept[IllegalArgumentException](TeachingChain(Nil))
    intercept[IllegalArgumentException](TeachingChain(List.fill(9)(TeachingBlock("x"))))
    for ((index, zeros, start, attempts) <- List((3, 1, 0, 1), (0, 0, 0, 1), (0, 5, 0, 1), (0, 1, -1, 1), (0, 1, 0, 0), (0, 1, 0, 1001)))
      intercept[IllegalArgumentException](mined.mine(index, zeros, start, attempts))
  }
  test("exercise grading and persistence enforce the configured block count") {
    val e = BlockchainInteraction("chain", LanguageMapContentId("test/chain"), mined)
    assert(e.isCorrect(mined))
    assert(!e.isCorrect(TeachingChain(List(mined.blocks.head))))
    intercept[SerializedException](e.serializerInteractionContent.deserialize(write(TeachingChain(List(mined.blocks.head)))))
    intercept[SerializedException](e.serializerInteractionContent.serialize(TeachingChain(List(mined.blocks.head))))
    val edited = mined.update(0, TeachingBlock("draft text\n🌍", 42))
    assertEquals(e.serializerInteractionContent.deserialize(e.serializerInteractionContent.serialize(edited)), edited)
    for (serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike))
      assertEquals(serializer.deserialize(serializer.serialize(e)), e)
  }
}
