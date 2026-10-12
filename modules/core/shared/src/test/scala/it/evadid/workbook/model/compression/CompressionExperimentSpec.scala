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
    val total = CompressionAlgorithms.wordCount(text)
    val full = CompressionAlgorithms.dictionary(text, total)
    assertEquals(full.decoded, text)
    assertEquals(CompressionAlgorithms.dictionary(text, 1).display, "Ein ")
    assertEquals(CompressionAlgorithms.dictionary(text, 3).display, "Ein Wort,\nW1 ")
    assertEquals(full.dictionary, List("Ein", "Wort,", "[1]"))
    assert(full.modelBytes > 0)
    assertEquals(CompressionAlgorithms.dictionary("", 0).modelBytes, 0)
    intercept[IllegalArgumentException](DictionaryText(text, total + 1))
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
    assertEquals(ImageBlocks("image.jpg").sampleBytes, 1843200)
    assertEquals(ImageBlocks("image.jpg", 64, 64).sampleBytes, 450)
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
  test("RLE challenges are sticky and photo capacity is decimal") {
    val first = RunLengthText().edit("A" * 12)
    assertEquals(first.achieved,List(0,2))
    assertEquals(first.edit("ABCDE").achieved,List(0,1,2))
    assertEquals(PhotoBudget(4).count,500L)
    assertEquals(PhotoBudget(2001).count,0L)
  }
  test("simulator applies original factors once and preserves metadata until explicitly removed") {
    val file = SimulationFile("note.docx",10000,"text",List("Autor: Redaktion","Auflösung: 1×1"))
    val lossless=CompressionSimulation.applyTool(file,"lossless")
    assertEquals(lossless.bytes,8600L)
    assertEquals(CompressionSimulation.applyTool(lossless,"lossless"),lossless)
    assert(lossless.problematic)
    val converted=CompressionSimulation.applyTool(file,"convert")
    assertEquals(converted.name,"note.txt")
    assertEquals(converted.bytes,2200L)
    assertEquals(converted.metadata,Nil)
    val raw=CompressionSimulation.applyTool(SimulationFile("a.raw",10000,"image"),"lossless")
    assertEquals(raw.bytes,4200L)
    assertEquals(raw.metadata,List("Auflösung: 4032×3024"))
    val encrypted=SimulationFile("a.enc.zip",10000,"encrypted")
    for tool <- List("convert","lossy","lossless") do assertEquals(CompressionSimulation.applyTool(encrypted,tool),encrypted)
  }
  test("every source file and sequential operation agrees with the retained original") {
    val fixtures = read[List[(SimulationFile,List[(String,SimulationFile)])]](OriginalCompressionFixtures.json)
    assertEquals(fixtures.size,420)
    for (initial,results) <- fixtures; (tool,expected) <- results do
      assertEquals(CompressionSimulation.applyTool(initial,tool),expected,s"${initial.name}, ${initial.applied}, $tool")
  }
  test("archives block concealed metadata, charge overhead, retain counts and count ineffective clicks") {
    val a=SimulationFile("a.txt",100000000,"text",count=100)
    val b=SimulationFile("b.mp4",200000000,"video",List("GPS: Park"))
    val initial=FileSimulation("test",List(a,b))
    val blocked=initial.choose("archive").click(0).click(1).archive
    assert(blocked.error)
    assertEquals(blocked.steps,0)
    assertEquals(blocked.files,initial.files)
    val cleaned=initial.choose("convert").click(1)
    val archived=cleaned.choose("archive").click(0).click(1).archive
    assertEquals(archived.files.size,1)
    assertEquals(archived.files.head.bytes,22000000L+194000000L+CompressionSimulation.archiveOverhead)
    assertEquals(archived.fileCount,101)
    assertEquals(archived.files.head.name,"logs_101_dateien.rar")
    assertEquals(archived.steps,2)
    assert(archived.complete)
    assertEquals(archived.choose("lossy").click(0).steps,3)
    assertEquals(archived.choose("lossy").click(0).files,archived.files)
  }
  test("every experiment round-trips definitions and saved values on the shared registry") {
    val examples: List[CompressionExperiment] = List(VideoBudget(), RunLengthText("ää😀"), DictionaryText("Wort Wort", 2),
      TextBits(flippedBit = Some(2)), ImageBlocks("programs/photo.jpg", 2, 4), ArchiveBudget(),
      WrittenAnswer("def encode(text):",true), EthicalReflection(Some(2),"Unsicher"),PhotoBudget(),BitComparison(),FileInspection("Sample text",expanded = List("docProps/core.xml")),TransferSimulation(List("a.txt" -> "ä"),2),
      FileSimulation("test",List(SimulationFile("a.txt",1000,"text"))), PreviousAnswer("answer-intro"),
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
