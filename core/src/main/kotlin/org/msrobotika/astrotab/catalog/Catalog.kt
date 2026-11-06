package org.msrobotika.astrotab.catalog

import java.io.DataInputStream
import java.io.InputStream
import org.msrobotika.astrotab.astro.Astro

class Catalog(input: InputStream) {
    val count: Int
    val ra: DoubleArray
    val dec: DoubleArray
    val magnitude: DoubleArray
    val colorIndex: ByteArray
    val constellation: IntArray
    val nameIndex: IntArray
    val pointsRa: DoubleArray
    val pointsDec: DoubleArray
    data class Poly(val constellation: Int, val first: Int, val count: Int)
    data class Label(val ra: Double, val dec: Double, val rank: Int)
    val polylines: List<Poly>
    val labels: List<Label>
    val names: List<String>
    val constellations: List<String>
    init {
        DataInputStream(input.buffered()).use { stream ->
            require(stream.readInt()==0x41544331) { "Catálogo no reconocido" }
            count=stream.readUnsignedShort()
            val pointCount=stream.readUnsignedShort(); val polyCount=stream.readUnsignedShort()
            val labelCount=stream.readUnsignedShort(); val nameCount=stream.readUnsignedShort()
            val conCount=stream.readUnsignedShort()
            require(count==5044 && pointCount==893 && polyCount==150 && labelCount==89 && nameCount==156 && conCount==89)
            ra=DoubleArray(count); dec=DoubleArray(count); magnitude=DoubleArray(count)
            colorIndex=ByteArray(count); constellation=IntArray(count); nameIndex=IntArray(count)
            repeat(count) { i ->
                ra[i]=stream.readUnsignedShort()*360.0/65536; dec[i]=stream.readShort()/100.0
                magnitude[i]=stream.readByte()/10.0; colorIndex[i]=stream.readByte()
                constellation[i]=stream.readUnsignedByte(); nameIndex[i]=stream.readUnsignedByte()
                require(dec[i] in -90.0..90.0 && magnitude[i] in -2.0..6.0)
                require(constellation[i]==255 || constellation[i]<conCount)
                require(nameIndex[i]==255 || nameIndex[i]<nameCount)
            }
            pointsRa=DoubleArray(pointCount); pointsDec=DoubleArray(pointCount)
            repeat(pointCount) { pointsRa[it]=stream.readUnsignedShort()*360.0/65536; pointsDec[it]=stream.readShort()/100.0 }
            polylines=List(polyCount) {
                Poly(stream.readUnsignedByte(),stream.readUnsignedShort(),stream.readUnsignedByte()).also {
                    require(it.constellation<conCount && it.first+it.count<=pointCount && it.count>=2)
                }
            }
            labels=List(labelCount) { Label(stream.readUnsignedShort()*360.0/65536,stream.readShort()/100.0,stream.readUnsignedByte()) }
            fun readText(): String { val bytes=ByteArray(stream.readUnsignedShort()); stream.readFully(bytes); return String(bytes,Charsets.UTF_8) }
            names=List(nameCount) { readText() }; constellations=List(conCount) { readText() }
            require(stream.read()==-1) { "Datos sobrantes en catálogo" }
        }
    }
    fun name(index: Int) = names.getOrNull(nameIndex[index]) ?: "Estrella ${index+1}"
    fun constellationName(index: Int) = constellations.getOrNull(constellation[index]) ?: "Sin asignar"
    fun visibleCount(limit: Double, lst: Double, latitude: Double): Int {
        var total=0
        for(i in 0 until count) if(magnitude[i]<=limit && Astro.toHorizon(ra[i],dec[i],lst,latitude).alt>=0) total++
        return total
    }
}
