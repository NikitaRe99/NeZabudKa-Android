package ru.nezabudka.app.widget

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import ru.nezabudka.app.data.SettingsStore
import ru.nezabudka.app.domain.defaultCustomTheme

/** Debug-only native host. It doesn't add or remove widgets on the user's launcher. */
class WidgetPreviewActivity : Activity() {
    private lateinit var host: AppWidgetHost
    lateinit var root: LinearLayout
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = AppWidgetHost(this, HOST_ID)
        val dp = resources.displayMetrics.density
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding((12 * dp).toInt(), (48 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt()) }
        setContentView(root)
        setPreviewTheme(SettingsStore(this).state.value.theme)
        (intent.getIntArrayExtra("widget_ids") ?: intArrayOf()).forEach { id ->
            val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id) ?: return@forEach
            root.addView(TextView(this).apply {
                text = info.loadLabel(packageManager); textSize = 14f; setTextColor(Color.GRAY)
                setPadding((4 * dp).toInt(), (12 * dp).toInt(), 0, (8 * dp).toInt())
            })
            root.addView(host.createView(this, id, info), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (230 * dp).toInt()))
        }
    }
    fun setPreviewTheme(theme: String) {
        root.setBackgroundColor(Color.parseColor(defaultCustomTheme(if (theme == "dark") "dark" else "light").background))
    }
    override fun onStart() { super.onStart(); host.startListening() }
    override fun onStop() { host.stopListening(); super.onStop() }
    companion object { const val HOST_ID = 773309 }
}
