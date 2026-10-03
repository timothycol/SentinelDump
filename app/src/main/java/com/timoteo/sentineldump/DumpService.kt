package com.timoteo.sentineldump

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DumpService : AccessibilityService() {

    companion object {
        const val TAG = "SentinelDump"
        const val ACTION_DUMP = "com.timoteo.sentineldump.ACTION_DUMP"
        var instance: DumpService? = null
            private set
    }

    private var btnDump: Button? = null
    private var wm: WindowManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        mostrarBotonFlotante()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DUMP) {
            volcar()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun mostrarBotonFlotante() {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16; y = 200
        }

        val btn = Button(this).apply {
            text = "DUMP"
            textSize = 16f
            setPadding(28, 28, 28, 28)
            setOnTouchListener(object : View.OnTouchListener {
                private var downX = 0f; private var downY = 0f
                private var startLPx = 0; private var startLPy = 0
                private var moved = false
                override fun onTouch(v: View, e: MotionEvent): Boolean {
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = e.rawX; downY = e.rawY
                            startLPx = lp.x; startLPy = lp.y; moved = false
                            return false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (e.rawX - downX).toInt()
                            val dy = (e.rawY - downY).toInt()
                            if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) moved = true
                            lp.x = startLPx - dx; lp.y = startLPy + dy
                            wm?.updateViewLayout(v, lp)
                            return true
                        }
                        MotionEvent.ACTION_UP -> { return moved }
                    }
                    return false
                }
            })
            setOnClickListener { volcar() }
        }

        wm?.addView(btn, lp)
        btnDump = btn
    }

    private fun volcar() {
        val root = rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "rootInActiveWindow es null")
            Toast.makeText(this, "Nada que leer en pantalla", Toast.LENGTH_SHORT).show()
            return
        }

        val out = JSONObject().apply {
            put("ts", System.currentTimeMillis())
            put("fecha", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            put("paquete", root.packageName?.toString())
            put("raiz", nodoAJson(root, 0))
        }

        val ok = escribir("dump_${System.currentTimeMillis()}.json", out.toString(2))
        Toast.makeText(this, if (ok) "Volcado listo ✔" else "Error al escribir ✖", Toast.LENGTH_SHORT).show()
    }

    private fun nodoAJson(n: AccessibilityNodeInfo, depth: Int): JSONObject {
        return JSONObject().apply {
            put("depth", depth)
            put("clase", n.className?.toString())
            put("id_vista", n.viewIdResourceName)
            put("texto", n.text?.toString())
            put("desc", n.contentDescription?.toString())
            put("clickable", n.isClickable)
            put("scrollable", n.isScrollable)
            val r = Rect(); n.getBoundsInScreen(r)
            put("bounds", JSONArray().put(r.left).put(r.top).put(r.right).put(r.bottom))
            val hijos = JSONArray()
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { hijos.put(nodoAJson(it, depth + 1)) }
            }
            put("hijos", hijos)
        }
    }

    private fun escribir(nombre: String, contenido: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val valores = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, nombre)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/SentinelDump")
                }
                val uri = contentResolver.insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
                    ?: return false
                contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(contenido.toByteArray(Charsets.UTF_8))
                } ?: return false
                true
            } else {
                val dir = java.io.File(getExternalFilesDir(null), "SentinelDump").apply { mkdirs() }
                java.io.File(dir, nombre).writeText(contenido, Charsets.UTF_8)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error escribiendo $nombre", e)
            false
        }
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        btnDump?.let { runCatching { wm?.removeView(it) } }
        btnDump = null
        super.onDestroy()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance == this) instance = null
        btnDump?.let { runCatching { wm?.removeView(it) } }
        btnDump = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}