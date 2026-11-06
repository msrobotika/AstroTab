package org.msrobotika.astrotab.ui

import java.io.DataInputStream
import java.io.InputStream
import org.msrobotika.astrotab.astro.Astro

/** MW01: anillos cerrados y niveles originales d3, simplificación RDP 0,2°. */
class MilkyWay(input: InputStream) {
    val starts: IntArray; val lengths: IntArray; val flags: IntArray
    val ra: FloatArray; val dec: FloatArray
    init {
        DataInputStream(input.buffered()).use { s ->
            require(s.readInt()==0x4D573031)
            val rings=s.readUnsignedShort(); val points=s.readUnsignedShort()
            starts=IntArray(rings); lengths=IntArray(rings); flags=IntArray(rings); ra=FloatArray(points); dec=FloatArray(points)
            var offset=0
            repeat(rings) { r -> flags[r]=s.readUnsignedByte(); starts[r]=offset; lengths[r]=s.readUnsignedShort()
                repeat(lengths[r]) { ra[offset]=s.readUnsignedShort()*360f/65536; dec[offset]=s.readShort()/100f; offset++ }
            }; require(offset==points && s.read()==-1)
        }
    }
    /** Inclusión angular con desenvoltura AR; anillos huecos se restan en cada nivel. */
    fun contains(rightAscension: Double,declination: Double): Boolean {
        for(layer in 0..4) {
            var inside=false
            for(r in starts.indices) {
                if(flags[r]/2!=layer) continue
                var hit=false; var j=starts[r]+lengths[r]-1
                for(i in starts[r] until starts[r]+lengths[r]) {
                    val ax=Astro.rev(ra[i]-rightAscension+180)-180; val bx=Astro.rev(ra[j]-rightAscension+180)-180
                    if(kotlin.math.abs(ax-bx)<180 && (dec[i]>declination)!=(dec[j]>declination) && 0 < (bx-ax)*(declination-dec[i])/(dec[j]-dec[i])+ax) hit=!hit
                    j=i
                }; if(hit) inside=!inside
            }; if(inside) return true
        }; return false
    }
}
