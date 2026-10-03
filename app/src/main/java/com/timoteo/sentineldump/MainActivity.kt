package com.timoteo.sentineldump

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var btnDump: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        val btnAcc = Button(this).apply { text = "1. Activar accesibilidad" }
        status = TextView(this)
        btnDump = Button(this).apply { text = "2. VOLCAR PANTALLA" }

        btnAcc.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnDump.setOnClickListener {
            if (DumpService.instance == null) {
                Toast.makeText(this, "Servicio no conectado", Toast.LENGTH_SHORT).show()
            } else {
                startService(Intent(this, DumpService::class.java).apply {
                    action = DumpService.ACTION_DUMP
                })
                Toast.makeText(this, "Volcado hecho. Revisa Download/SentinelDump", Toast.LENGTH_LONG).show()
            }
            refresh()
        }

        layout.addView(btnAcc); layout.addView(status); layout.addView(btnDump)
        setContentView(layout)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val on = isServiceEnabled()
        status.text = if (on)
            "✔ Servicio activo.\n\nAbre inDrive en la pantalla que quieras (lista, popup o historial), vuelve aquí y pulsa VOLCAR.\n\nLos archivos quedan en Download/SentinelDump."
        else
            "✖ Servicio inactivo. Pulsa el botón, busca 'Sentinel Dump' y actívalo."
        btnDump.isEnabled = on
    }

    private fun isServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            .any { it.resolveInfo.serviceInfo.packageName == "com.timoteo.sentineldump" }
    }
}