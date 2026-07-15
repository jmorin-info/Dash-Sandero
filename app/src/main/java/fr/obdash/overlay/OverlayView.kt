package fr.obdash.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import fr.obdash.core.VehicleState

/**
 * Widget flottant compact affiche PAR-DESSUS Waze : vitesse, rapport engage, régime,
 * conso et température d'eau. Deplacable au doigt. Palette alignee sur l'app.
 */
@SuppressLint("ViewConstructor")
class OverlayView(context: Context) : View(context) {
    private var wm: WindowManager? = null
    private var lp: WindowManager.LayoutParams? = null
    private var state = VehicleState()

    private val d = resources.displayMetrics.density
    private val bg = Paint().apply { color = Color.argb(224, 14, 18, 24); isAntiAlias = true }
    private val speedP = Paint().apply { color = Color.rgb(234, 242, 255); textSize = 30 * d; isFakeBoldText = true; isAntiAlias = true }
    private val gearP = Paint().apply { color = Color.rgb(255, 176, 32); textSize = 30 * d; isFakeBoldText = true; isAntiAlias = true; textAlign = Paint.Align.RIGHT }
    private val accentP = Paint().apply { color = Color.rgb(53, 217, 160); textSize = 14 * d; isFakeBoldText = true; isAntiAlias = true }
    private val dimP = Paint().apply { color = Color.rgb(126, 138, 154); textSize = 12 * d; isAntiAlias = true }

    private var downX = 0f; private var downY = 0f
    private var startX = 0; private var startY = 0

    fun attach(windowManager: WindowManager, params: WindowManager.LayoutParams) {
        wm = windowManager; lp = params
    }

    fun update(s: VehicleState) { state = s; invalidate() }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension((214 * d).toInt(), (96 * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawRoundRect(0f, 0f, w, h, 18 * d, 18 * d, bg)

        // Ligne haute : vitesse (gauche) + rapport (droite)
        canvas.drawText("${state.speedKmh ?: "--"}", 16 * d, 40 * d, speedP)
        canvas.drawText("km/h", 16 * d, 55 * d, dimP)
        val gear = when (state.gear) { null -> "-"; 0 -> "N"; else -> "${state.gear}" }
        canvas.drawText(gear, w - 16 * d, 40 * d, gearP)
        canvas.drawText("rapport", w - 16 * d, 55 * d, dimP.apply { textAlign = Paint.Align.RIGHT })
        dimP.textAlign = Paint.Align.LEFT

        // Ligne basse : régime + conso + eau
        val conso = state.instL100?.let { String.format("%.1f L/100", it) }
            ?: state.instLph?.let { String.format("%.1f L/h", it) } ?: "-- L"
        canvas.drawText("${state.rpm ?: "--"} tr", 16 * d, 80 * d, dimP)
        canvas.drawText(conso, 92 * d, 80 * d, accentP)
        canvas.drawText("${state.coolantC ?: "--"}\u00B0", w - 16 * d, 80 * d, dimP.apply { textAlign = Paint.Align.RIGHT })
        dimP.textAlign = Paint.Align.LEFT
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val p = lp ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX; downY = event.rawY; startX = p.x; startY = p.y
            }
            MotionEvent.ACTION_MOVE -> {
                p.x = startX + (event.rawX - downX).toInt()
                p.y = startY + (event.rawY - downY).toInt()
                wm?.updateViewLayout(this, p)
            }
        }
        return true
    }
}
