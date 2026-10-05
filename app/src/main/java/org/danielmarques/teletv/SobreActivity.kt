package org.danielmarques.teletv

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SobreActivity : AppCompatActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val texto = TextView(this).apply {
            textSize = 17f
            setTextColor(getColor(R.color.texto))
            setLineSpacing(0f, 1.25f)
            setPadding(160, 48, 160, 48)
            text = getString(R.string.about_text, BuildConfig.VERSION_NAME, BuildConfig.REPO, ApoioActivity.BITCOIN)
        }
        // Focável para as setas do controle rolarem o texto.
        setContentView(ScrollView(this).apply {
            isFocusable = true
            addView(texto)
        })
    }
}
