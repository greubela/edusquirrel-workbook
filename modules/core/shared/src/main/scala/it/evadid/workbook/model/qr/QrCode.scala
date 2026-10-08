package it.evadid.workbook.model.qr

import upickle.default.*
import java.nio.charset.StandardCharsets

/** The format-information bits differ from the ordinal ordering of correction strength. */
enum QrErrorCorrection(val formatBits: Int) derives ReadWriter {
  case L extends QrErrorCorrection(1)
  case M extends QrErrorCorrection(0)
  case Q extends QrErrorCorrection(3)
  case H extends QrErrorCorrection(2)
}

/** Resolved metadata: version and mask are the ones actually used, never “automatic”.
  * ECI 26 identifies UTF-8. Raw bytes use no ECI unless requested explicitly.
  */
case class QrCodeConfig(version: Int = 1, errorCorrection: QrErrorCorrection = QrErrorCorrection.M,
                        mask: Int = 0, utf8: Boolean = false) derives ReadWriter {
  require(version >= 1 && version <= 40, "QR version must be between 1 and 40")
  require(mask >= 0 && mask <= 7, "QR mask must be between 0 and 7")
}

/** Immutable payload with value equality, including bytes. The symbol is derived rather than persisted.
  * Implements QR Model 2 byte mode (ISO/IEC 18004); no QR library is needed at runtime.
  */
final class QrCode private (private val bytes: Vector[Byte], val config: QrCodeConfig) {
  def content: Array[Byte] = bytes.toArray
  def byteCount: Int = bytes.size
  def text: String = new String(content, StandardCharsets.UTF_8)
  val size: Int = 17 + 4 * config.version
  lazy val modules: Vector[Vector[Boolean]] = QrCodeEncoder.matrix(content, config)
  def isDark(x: Int, y: Int): Boolean = modules(y)(x)
  def withMask(mask: Int): QrCode = QrCode(content, config.copy(mask = mask))
  def maskPenalties: Vector[Int] = (0 to 7).map(m => QrCodeEncoder.penalty(withMask(m).modules)).toVector
  def withBestMask: QrCode = withMask(maskPenalties.zipWithIndex.minBy(p => (p._1, p._2))._2)
  override def equals(other: Any): Boolean = other match {
    case that: QrCode => bytes == that.bytes && config == that.config
    case _ => false
  }
  override def hashCode(): Int = (bytes, config).hashCode()
  override def toString: String = s"QrCode($byteCount bytes, $config)"
}

object QrCode {
  def apply(content: Array[Byte], config: QrCodeConfig): QrCode = {
    require(content.length <= capacity(config), s"Content exceeds version ${config.version} capacity (${capacity(config)} bytes)")
    new QrCode(content.toVector, config)
  }
  def capacity(config: QrCodeConfig): Int =
    (QrCodeEncoder.dataCapacity(config.version, config.errorCorrection) * 8 -
      (if config.utf8 then 12 else 0) - 4 - (if config.version < 10 then 8 else 16)) / 8

  /** Select the smallest fitting version and the lowest-penalty mask; explicit choices stay explicit. */
  def encode(content: Array[Byte], errorCorrection: QrErrorCorrection = QrErrorCorrection.M,
             version: Option[Int] = None, mask: Option[Int] = None, utf8: Boolean = false): QrCode = {
    version.foreach(v => require(v >= 1 && v <= 40, "QR version must be between 1 and 40"))
    mask.foreach(m => require(m >= 0 && m <= 7, "QR mask must be between 0 and 7"))
    val chosenVersion = version.getOrElse((1 to 40).find(v =>
      content.length <= capacity(QrCodeConfig(v, errorCorrection, 0, utf8)))
      .getOrElse(throw new IllegalArgumentException("Content exceeds QR version 40 capacity")))
    val code = apply(content, QrCodeConfig(chosenVersion, errorCorrection, mask.getOrElse(0), utf8))
    if mask.isDefined then code else code.withBestMask
  }
  def fromText(text: String, errorCorrection: QrErrorCorrection = QrErrorCorrection.M,
               version: Option[Int] = None, mask: Option[Int] = None): QrCode =
    encode(text.getBytes(StandardCharsets.UTF_8), errorCorrection, version, mask, utf8 = true)

  given ReadWriter[QrCode] = readwriter[ujson.Value].bimap[QrCode](
    q => ujson.Obj("content" -> writeJs(q.content), "config" -> writeJs(q.config)),
    j => apply(read[Array[Byte]](j("content")), read[QrCodeConfig](j("config"))))
}
