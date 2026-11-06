package org.msrobotika.astrotab.ui

import android.content.Context
import android.graphics.*
import android.view.View
import org.msrobotika.astrotab.ui.theme.*

@android.annotation.SuppressLint("ViewConstructor")
class MiniAltitude(context: Context,private val altitudes: DoubleArray,red: Boolean): View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG); private val path=Path(); private val palette=Palette(red)
    init { contentDescription="Curva de altura de la noche, horizonte a cero grados" }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        path.rewind()
        fun x(i: Int)=4+(w-8)*i.toFloat()/altitudes.lastIndex.coerceAtLeast(1)
        fun y(i: Int)=h-4-((altitudes[i]+30)/120).toFloat()*(h-8)
        if(altitudes.isNotEmpty()) path.moveTo(x(0),y(0))
        for(i in 0 until altitudes.lastIndex) {
            val previous=(i-1).coerceAtLeast(0); val next=(i+2).coerceAtMost(altitudes.lastIndex)
            path.cubicTo(x(i)+(x(i+1)-x(previous))/6,y(i)+(y(i+1)-y(previous))/6,
                x(i+1)-(x(next)-x(i))/6,y(i+1)-(y(next)-y(i))/6,x(i+1),y(i+1))
        }
    }
    override fun onDraw(c: Canvas) { p.color=palette.raised; p.strokeWidth=1f; val horizon=height-4-(height-8)/4f; c.drawLine(0f,horizon,width.toFloat(),horizon,p); p.color=palette.accent; p.strokeWidth=2f; p.style=Paint.Style.STROKE; c.drawPath(path,p); p.style=Paint.Style.FILL }
}
