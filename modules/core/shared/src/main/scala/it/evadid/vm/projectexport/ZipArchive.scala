package it.evadid.vm.projectexport

import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDateTime

/**
 * A minimal ZIP writer without dependencies, so the browser and the JVM produce identical archives.
 * Entries are stored uncompressed (method 0) with CRC-32 checksums and UTF-8 names; paths ending in
 * `/` are directories. Suitable for small projects: at most 65 535 entries and 4 GiB.
 */
object ZipArchive {
  final case class Entry(path: String, content: Array[Byte]) {
    require(path.nonEmpty && !path.startsWith("/") && !path.split('/').contains(".."), s"Invalid archive path: $path")
    def isDirectory: Boolean = path.endsWith("/")
  }

  private val crcTable: Array[Int] = Array.tabulate(256) { n =>
    (0 until 8).foldLeft(n)((c, _) => if (c & 1) != 0 then 0xEDB88320 ^ (c >>> 1) else c >>> 1)
  }

  def crc32(bytes: Array[Byte]): Int =
    ~bytes.foldLeft(0xFFFFFFFF)((crc, byte) => crcTable((crc ^ byte) & 0xFF) ^ (crc >>> 8))

  /** MS-DOS date and time; ZIP cannot represent years before 1980. */
  private def dosDateTime(time: LocalDateTime): (Int, Int) = {
    val t = if time.getYear < 1980 then LocalDateTime.of(1980, 1, 1, 0, 0) else time
    (((t.getYear - 1980) << 9) | (t.getMonthValue << 5) | t.getDayOfMonth,
      (t.getHour << 11) | (t.getMinute << 5) | (t.getSecond / 2))
  }

  private def le16(value: Int): Array[Byte] = Array(value.toByte, (value >>> 8).toByte)
  private def le32(value: Int): Array[Byte] = le16(value & 0xFFFF) ++ le16(value >>> 16)

  def write(entries: Seq[Entry], modified: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)): Array[Byte] = {
    require(entries.size < 0xFFFF, "Too many entries for a ZIP archive without ZIP64.")
    require(entries.map(_.path).distinct.size == entries.size, "Archive paths must be unique.")
    val (date, time) = dosDateTime(modified)
    val prepared = entries.map { entry =>
      val name = entry.path.getBytes(UTF_8)
      val data = if entry.isDirectory then Array.emptyByteArray else entry.content
      // Version needed, UTF-8 names flag, stored, time, date, CRC, both sizes, name length, no extra field.
      val header = Array.concat(le16(20), le16(0x0800), le16(0), le16(time), le16(date), le32(crc32(data)),
        le32(data.length), le32(data.length), le16(name.length), le16(0))
      (entry, name, data, header)
    }
    val locals = prepared.map((_, name, data, header) => Array.concat(le32(0x04034b50), header, name, data))
    val offsets = locals.scanLeft(0L)(_ + _.length)
    require(offsets.last < 0xFFFFFFFFL, "The archive exceeds 4 GiB.")
    val central = prepared.zip(offsets).map { case ((entry, name, _, header), offset) =>
      // Version made by, the shared header, no comment, disk 0, no internal attributes, directory flag, offset.
      Array.concat(le32(0x02014b50), le16(20), header, le16(0), le16(0), le16(0),
        le32(if entry.isDirectory then 0x10 else 0), le32(offset.toInt), name)
    }
    val directory = Array.concat(central *)
    val end = Array.concat(le32(0x06054b50), le16(0), le16(0), le16(entries.size), le16(entries.size),
      le32(directory.length), le32(offsets.last.toInt), le16(0))
    Array.concat(Array.concat(locals *), directory, end)
  }
}
