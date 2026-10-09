package it.evadid.workbook.model.compression

import it.evadid.core.datastructures.language.LanguageMapContentId
import it.evadid.workbook.elements.interactionElements.compression.CompressionExperimentInteraction
import it.evadid.workbook.jsonFactory.WorkbookElementFactory
import upickle.default.*

class CompressionExperimentSpec extends munit.FunSuite {
  test("video sizing converts megabits to decimal megabytes and handles exact capacity") {
    val video = VideoBudget(5, 300, 10, 2000)
    assertEquals(video.megabytes, 1875.0)
    assert(video.fits)
    assert(!video.copy(copies = 100).fits)
    assert(VideoBudget(8, 1, 1, 1).fits)
    intercept[IllegalArgumentException](VideoBudget(Double.NaN))
    intercept[IllegalArgumentException](VideoBudget(copies = 0))
  }
  test("RLE preserves Unicode, whitespace and empty text and accounts for multidigit counts") {
    for (text <- List("", "AAABBBCCDDDDDA", "😀😀üü\n\n  ", "A" * 12))
      assertEquals(CompressionAlgorithms.decodeRuns(CompressionAlgorithms.runs(text)), text)
    assertEquals(CompressionAlgorithms.runs("😀😀A"), List(CharacterRun("😀", 2), CharacterRun("A", 1)))
    assertEquals(CompressionAlgorithms.runBytes(CompressionAlgorithms.runs("A" * 12)), 3)
    assert(CompressionAlgorithms.runBytes(CompressionAlgorithms.runs("ABCDE")) > CompressionAlgorithms.utf8Bytes("ABCDE"))
  }
  test("dictionary steps preserve spacing and punctuation and include the dictionary cost") {
    val text = "Ein Wort,\nEin Wort,  [1]"
    val tokens = CompressionAlgorithms.tokens(text)
    for (step <- 0 to tokens.size)
      assertEquals(CompressionAlgorithms.dictionary(text, step).decoded, tokens.take(step).mkString)
    val full = CompressionAlgorithms.dictionary(text, tokens.size)
    assertEquals(full.dictionary, List("Ein", "Wort,", "[1]"))
    assert(full.modelBytes > 0)
    assertEquals(CompressionAlgorithms.dictionary("", 0).modelBytes, 0)
    intercept[IllegalArgumentException](DictionaryText(text, tokens.size + 1))
  }
  test("one password bit changes exactly one byte and can be restored") {
    val original = TextBits("ABC")
    for (i <- 0 until 24) {
      val changed = original.copy(flippedBit = Some(i))
      assertEquals(original.bytes.zip(changed.bytes).count((a, b) => a != b), 1)
      assertEquals(Integer.bitCount(original.bytes(i / 8) ^ changed.bytes(i / 8)), 1)
      assertEquals(changed.copy(flippedBit = None).changed, original.original)
    }
    intercept[IllegalArgumentException](original.copy(flippedBit = Some(24)))
  }
  test("block averaging preserves means, edge blocks and independent luminance/chroma") {
    assertEquals(CompressionAlgorithms.averageBlocks(Vector(0.0, 2.0, 8.0, 2.0, 4.0, 10.0), 3, 2, 2),
      Vector(2.0, 2.0, 9.0, 2.0, 2.0, 9.0))
    val rgb = Vector((255, 0, 0), (0, 255, 0), (0, 0, 255), (255, 255, 255))
    assertEquals(CompressionAlgorithms.imageBlocks(rgb, 2, 2, 1, 1), rgb)
    assertEquals(CompressionAlgorithms.imageBlocks(rgb, 2, 2, 2, 2).distinct.size, 1)
    assert(CompressionAlgorithms.imageBlocks(rgb, 2, 2, 1, 2).distinct.size > 1)
    assertEquals(ImageBlocks("image.jpg").sampleBytes, 12288)
    assertEquals(ImageBlocks("image.jpg", 64, 64).sampleBytes, 3)
    intercept[IllegalArgumentException](ImageBlocks("image.jpg", 3))
  }
  test("an uncompressed archive adds bytes but may save or cost time depending on overhead") {
    val archive = ArchiveBudget()
    assertEquals(archive.archiveBytes - archive.payloadBytes, 320L)
    assert(archive.archiveSeconds < archive.individualSeconds)
    val freeOpening = archive.copy(openMilliseconds = 0)
    assert(freeOpening.archiveSeconds > freeOpening.individualSeconds)
    assertEquals(archive.copy(fileCount = 10000, bytesPerFile = 10000000).payloadBytes, 100000000000L)
  }
  test("every experiment round-trips definitions and saved values on the shared registry") {
    val examples: List[CompressionExperiment] = List(VideoBudget(), RunLengthText("ää😀"), DictionaryText("Wort Wort", 3),
      TextBits(flippedBit = Some(2)), ImageBlocks("programs/photo.jpg", 2, 4), ArchiveBudget(),
      StorageStudy(List(StoragePackage(LanguageMapContentId("CompressionWorkbook/label"),
        List(StorageFile("name", 40000, LanguageMapContentId("CompressionWorkbook/info")))))))
    for (value <- examples) {
      val exercise = CompressionExperimentInteraction("experiment", LanguageMapContentId("CompressionWorkbook/title"), value)
      for (serializer <- List(WorkbookElementFactory.serializerRefBasedJson, WorkbookElementFactory.serializerConstructorLike))
        assertEquals(serializer.deserialize(serializer.serialize(exercise)), exercise)
      val saved = exercise.serializerInteractionContent
      assertEquals(saved.deserialize(saved.serialize(value)), value)
    }
    val rle = CompressionExperimentInteraction("rle", LanguageMapContentId("CompressionWorkbook/title"), RunLengthText())
    intercept[it.evadid.distribution.command.SerializedException](rle.serializerInteractionContent.deserialize(write[CompressionExperiment](VideoBudget())))
  }
}
