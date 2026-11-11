package com.premiumcar.launcher.perf

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.text.TextUtils
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.premiumcar.launcher.design.PcPalette
import kotlin.math.roundToInt

/**
 * Mesmo teste de [ComposeBenchmarkActivity], implementado com Views nativas
 * (GridView reciclando células). Roda no processo `:bench_views`.
 */
class ViewBenchmarkActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var header: TextView
    private lateinit var grid: GridView
    private var coldStart = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coldStart = StartupTimeline.markActivityCreated("Teste de fluidez (Views)")
        if (coldStart) StartupTimeline.trackFirstFrame(window.decorView)

        header = TextView(this).apply {
            text = "Teste de fluidez · Views — carregando ícones…"
            setTextColor(PcPalette.TEXT_PRIMARY)
            textSize = 18f
            val padding = dp(16)
            setPadding(padding, padding, padding, padding)
        }
        grid = GridView(this).apply {
            numColumns = GridView.AUTO_FIT
            columnWidth = dp(BenchmarkSpec.TILE_MIN_WIDTH_DP)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing = dp(BenchmarkSpec.SPACING_DP)
            verticalSpacing = dp(BenchmarkSpec.SPACING_DP)
            val padding = dp(BenchmarkSpec.PADDING_DP)
            setPadding(padding, padding, padding, padding)
            clipToPadding = false
            selector = ColorDrawable(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            // Ignora toques durante o teste (equivale a userScrollEnabled = false no Compose).
            setOnTouchListener { _, _ -> true }
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(PcPalette.BACKGROUND)
                addView(header, LinearLayout.LayoutParams(MATCH, WRAP))
                addView(grid, LinearLayout.LayoutParams(MATCH, 0, 1f))
            },
        )

        val iconPx = dp(BenchmarkSpec.ICON_SIZE_DP)
        val appContext = applicationContext
        Thread {
            val loaded = BenchmarkData.load(appContext, iconPx)
            runOnUiThread { if (!isFinishing) onLoaded(loaded) }
        }.start()
    }

    private fun onLoaded(loaded: LoadedItems) {
        grid.adapter = TileAdapter(
            items = loaded.items,
            tileHeight = dp(BenchmarkSpec.TILE_HEIGHT_DP),
            iconSize = dp(BenchmarkSpec.ICON_SIZE_DP),
            cornerRadius = dp(BenchmarkSpec.CORNER_DP).toFloat(),
            innerSpacing = dp(8),
        )
        handler.postDelayed({ runScroll(loaded) }, BenchmarkSpec.SETTLE_MILLIS)
    }

    private fun runScroll(loaded: LoadedItems) {
        if (isFinishing) return
        header.text = "Teste de fluidez · Views — rolando, não toque na tela"
        val recorder = FrameRecorder().apply { start() }
        val pxPerSecond = BenchmarkSpec.SPEED_DP_PER_SECOND * resources.displayMetrics.density
        val choreographer = Choreographer.getInstance()
        choreographer.postFrameCallback(object : Choreographer.FrameCallback {
            private var direction = 1
            private var phaseStart = 0L
            private var last = 0L
            private var remainder = 0f

            override fun doFrame(now: Long) {
                if (isFinishing) return
                if (phaseStart == 0L) {
                    phaseStart = now
                    last = now
                }
                if (now - phaseStart >= BenchmarkSpec.DIRECTION_MILLIS * 1_000_000) {
                    if (direction < 0) {
                        finishBenchmark(ENGINE, coldStart, loaded, recorder.stop())
                        return
                    }
                    direction = -1
                    phaseStart = now
                    last = now
                }
                // Acumula a fração de pixel para manter a mesma velocidade do teste em Compose.
                val exact = pxPerSecond * (now - last) / 1_000_000_000f * direction + remainder
                val dy = exact.toInt()
                remainder = exact - dy
                last = now
                if (dy != 0) grid.scrollListBy(dy)
                choreographer.postFrameCallback(this)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        // Encerra o processo de teste para que a próxima execução também seja a frio.
        if (isFinishing) Process.killProcess(Process.myPid())
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        const val ENGINE = "Views"
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}

private class TileAdapter(
    private val items: List<BenchItem>,
    private val tileHeight: Int,
    private val iconSize: Int,
    private val cornerRadius: Float,
    private val innerSpacing: Int,
) : BaseAdapter() {
    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val tile = convertView as? TileView
            ?: TileView(parent.context, tileHeight, iconSize, cornerRadius, innerSpacing)
        tile.bind(items[position])
        return tile
    }
}

private class TileView(
    context: Context,
    tileHeight: Int,
    iconSize: Int,
    cornerRadius: Float,
    innerSpacing: Int,
) : LinearLayout(context) {
    private val icon = ImageView(context)
    private val label = TextView(context).apply {
        setTextColor(PcPalette.TEXT_PRIMARY)
        textSize = BenchmarkSpec.LABEL_SP.toFloat()
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        layoutParams = AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, tileHeight)
        background = GradientDrawable().apply {
            this.cornerRadius = cornerRadius
            setColor(PcPalette.SURFACE)
        }
        setPadding(innerSpacing, innerSpacing, innerSpacing, innerSpacing)
        addView(icon, LayoutParams(iconSize, iconSize))
        addView(label, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = innerSpacing })
    }

    fun bind(item: BenchItem) {
        icon.setImageBitmap(item.icon)
        label.text = item.label
    }
}
