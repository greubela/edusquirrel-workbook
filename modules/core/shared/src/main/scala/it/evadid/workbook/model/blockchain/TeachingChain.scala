package it.evadid.workbook.model.blockchain

import upickle.default.*

import java.nio.charset.StandardCharsets

/** Educational text blocks, with a deliberately explicit format rather than Bitcoin headers. */
case class TeachingBlock(data: String, nonce: Int = 0) derives ReadWriter {
  require(data.length <= TeachingChain.maxDataLength, "Block text is too long")
  require(nonce >= 0, "A teaching nonce must be nonnegative")

  def hash(index: Int, previousHash: String): String = {
    require(index >= 0 && previousHash.matches("[0-9a-f]{64}"), "Invalid block position or predecessor hash")
    val bytes = data.getBytes(StandardCharsets.UTF_8)
    val header = s"edusquirrel-teaching-block-v1\n$index\n$previousHash\n$nonce\n${bytes.length}\n"
      .getBytes(StandardCharsets.UTF_8)
    Sha256.hex(header ++ bytes)
  }
}

case class MiningResult(nonce: Int, attempts: Int, found: Boolean) {
  def nextNonce: Option[Int] = Option.when(nonce < Int.MaxValue)(nonce + 1)
}

case class TeachingChain(blocks: List[TeachingBlock]) derives ReadWriter {
  require(blocks.nonEmpty && blocks.size <= TeachingChain.maxBlocks, "A teaching chain contains one to eight blocks")
  lazy val hashes: List[String] = blocks.zipWithIndex.foldLeft(List.empty[String]) { case (previous, (block, index)) =>
    previous :+ block.hash(index, previous.lastOption.getOrElse(TeachingChain.genesisPreviousHash))
  }

  def previousHash(index: Int): String = {
    require(index >= 0 && index < blocks.size, "Invalid block index")
    if index == 0 then TeachingChain.genesisPreviousHash else hashes(index - 1)
  }

  def proofs(zeros: Int): List[Boolean] = {
    TeachingChain.validateDifficulty(zeros)
    hashes.map(_.startsWith("0" * zeros))
  }

  def validPrefixLength(zeros: Int): Int = proofs(zeros).takeWhile(identity).size

  def update(index: Int, block: TeachingBlock): TeachingChain = {
    require(index >= 0 && index < blocks.size, "Invalid block index")
    copy(blocks = blocks.updated(index, block))
  }

  /** At most 1000 trials per call, with no integer wraparound at the last nonce. */
  def mine(index: Int, zeros: Int, startNonce: Int, maxAttempts: Int): MiningResult = {
    require(index >= 0 && index < blocks.size && startNonce >= 0, "Invalid mining start")
    require(maxAttempts >= 1 && maxAttempts <= 1000, "Mining batches must contain 1–1000 attempts")
    TeachingChain.validateDifficulty(zeros)
    val previous = previousHash(index)
    var nonce = startNonce
    var attempts = 0
    var found = false
    var finished = false
    while (!finished) {
      attempts += 1
      found = blocks(index).copy(nonce = nonce).hash(index, previous).startsWith("0" * zeros)
      finished = found || attempts >= maxAttempts || nonce == Int.MaxValue
      if !finished then nonce += 1
    }
    MiningResult(nonce, attempts, found)
  }
}

object TeachingChain {
  val maxBlocks = 8
  val maxDataLength = 4096
  val genesisPreviousHash: String = "0" * 64

  def validateDifficulty(zeros: Int): Unit = require(zeros >= 1 && zeros <= 4, "Use one to four leading hexadecimal zeros")

  def parseNonce(raw: String): Option[Int] =
    if raw.nonEmpty && raw.length <= 10 && raw.forall(c => c >= '0' && c <= '9') then raw.toIntOption else None
}
