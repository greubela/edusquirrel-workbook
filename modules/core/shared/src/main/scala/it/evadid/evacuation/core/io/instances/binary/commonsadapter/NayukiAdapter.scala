package it.evadid.evacuation.core.io.instances.binary.commonsadapter

import java.io.ByteArrayOutputStream

import it.evadid.evacuation.core.io.traits.converter.Converter


class NayukiAdapter extends Converter[Array[Byte]] {


  override def convert(in: Array[Byte]): Array[Byte] = {

    val bout = new ByteArrayOutputStream()
  /*  val dout = new DeflaterOutputStream(bout)

    dout.write(in, 0, in.length)
    dout.flush()
    dout.close()
    bout.flush()

    //bout.toByteArray*/
    in

  }

  override def reconstruct(out: Array[Byte]): Array[Byte] = ???
}
