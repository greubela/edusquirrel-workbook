package it.evadid.workbook.elements.interactionElements.blockchain

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import it.evadid.workbook.model.blockchain.Sha256
import munit.FunSuite

class Sha256Spec extends FunSuite {
  private val title = LanguageMapContentId("test/sha256")
  test("FIPS empty, short and multi-block text vectors") {
    for ((text, expected) <- List(
      "" -> "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      "abc" -> "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
      "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq" -> "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
      ("abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmn" +
        "hijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu") ->
        "cf5b16a778af8380036ce59e7b0492370b249b11e8f07a51afac45037afee9d1"))
      assertEquals(Sha256.text(text), expected)
  }
  test("independent binary vectors across padding boundaries and unsigned byte values") {
    // Reference digests from Python hashlib; the test has no crypto-library dependency.
    for ((length, expected) <- List(
      55 -> "463eb28e72f82e0a96c0a4cc53690c571281131f672aa229e0d45ae59b598b59",
      56 -> "da2ae4d6b36748f2a318f23e7ab1dfdf45acdc9d049bd80e59de82a60895f562",
      63 -> "29af2686fd53374a36b0846694cc342177e428d1647515f078784d69cdb9e488",
      64 -> "fdeab9acf3710362bd2658cdc9a29e8f9c757fcf9811603a8c447cd1d9151108",
      65 -> "4bfd2c8b6f1eec7a2afeb48b934ee4b2694182027e6d0fc075074f2fabb31781",
      127 -> "92ca0fa6651ee2f97b884b7246a562fa71250fedefe5ebf270d31c546bfea976",
      128 -> "471fb943aa23c511f6f72f8d1652d9c880cfa392ad80503120547703e56a2be5",
      256 -> "40aff2e9d2d8922e47afd4648e6967497158785fbd1da870e7110266bf944880"))
      assertEquals(Sha256.hex(Array.tabulate[Byte](length)(_.toByte)), expected)
  }
  test("UTF-8 includes supplementary characters and preserves exact input spelling") {
    assertEquals(Sha256.text("Grüße 🌍"), "8db31f8a6161989c85a6209bc474492b5431c306c9319dd648c1b8682ad5c880")
    assertNotEquals(Sha256.text("abc"), Sha256.text(" abc"))
    assertNotEquals(Sha256.text("abc"), Sha256.text("abc\n"))
    assertNotEquals(Sha256.text("é"), Sha256.text("e\u0301"))
  }
  test("source capitalization experiment counts changed bits, not changed hex characters") {
    assertEquals(Sha256.text("Informatik"), "177379755e5f0476cbe382d89540dbe674a480ddf33bb6c535e3911e29ac9781")
    assertEquals(Sha256.text("informatik"), "64ce34aebb65ce365c7851235ef0f3d0e7fad5edc4158cd793a28427d182c956")
    assertEquals(Sha256.differingBits("Informatik", "informatik"), 134)
    assertEquals(Sha256.differingBits("abc", "abc"), 0)
    assertEquals(Sha256.differingBits("informatik", "Informatik"), 134)
  }
  test("calculations never mutate input and returned digest arrays are independent") {
    val bytes = Array[Byte](0, -128, -1)
    val before = bytes.toList
    val expected = Sha256.hex(bytes)
    val result = Sha256.digest(bytes)
    assertEquals(result.length, 32)
    result(0) = (result(0) ^ 255).toByte
    assertEquals(bytes.toList, before)
    assertEquals(Sha256.hex(bytes), expected)
  }
  test("prefix tasks grade actual hexadecimal digits and accept every matching input") {
    val task = Sha256Interaction("prefix", title, FindSha256Prefix(1))
    assertEquals(task.isCorrect(Sha256Answer("39")), Some(true))
    assertEquals(task.isCorrect(Sha256Answer("286")), Some(true))
    assertEquals(task.isCorrect(Sha256Answer("abc")), Some(false))
    assertEquals(task.isCorrect(Sha256Answer()), Some(false))
    assertEquals(task.copy(task = FindSha256Prefix(2)).isCorrect(Sha256Answer("39")), Some(false))
    assertEquals(task.copy(task = FindSha256Prefix(2)).isCorrect(Sha256Answer("286")), Some(true))
    assertEquals(task.hashes(Sha256Answer("39")).size, 1)
    assertEquals(task.hashes(Sha256Answer("39")).head.length, 64)
    assert(task.hashes(Sha256Answer("286")).head.startsWith("00"))
  }
  test("comparison remains ungraded, including identical and empty inputs") {
    val task = Sha256Interaction("compare", title)
    assertEquals(task.isCorrect(Sha256Answer()), None)
    assertEquals(task.hashes(Sha256Answer()), List.fill(2)(Sha256.text("")))
    assertEquals(task.isCorrect(Sha256Answer("same", "same")), None)
  }
  test("stored inputs are bounded and the complete maximum-size input is hashed") {
    val answer = Sha256Answer("a" * Sha256Interaction.maxTextLength)
    assertEquals(Sha256.text(answer.first), "c93eee2d0db02f10acc7460d9576e122dcf8cd53c4bf8dfcae1b3e74ebcfff5a")
    intercept[IllegalArgumentException](Sha256Answer("a" * 4097))
    intercept[IllegalArgumentException](Sha256Answer(second = "a" * 4097))
    intercept[IllegalArgumentException](FindSha256Prefix(0))
    intercept[IllegalArgumentException](FindSha256Prefix(5))
  }
  test("all task definitions and raw learner values round-trip in both workbook formats") {
    for (task <- List(CompareSha256(), FindSha256Prefix(1), FindSha256Prefix(2), FindSha256Prefix(4));
         serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike)) {
      val e = Sha256Interaction("sha", title, task, Sha256Answer("Grüße 🌍\n", ""))
      assertEquals(serializer.deserialize(serializer.serialize(e)), e)
      assertEquals(e.serializerInteractionContent.deserialize(e.serializerInteractionContent.serialize(e.initial)), e.initial)
    }
  }
}
