package org.danielmarques.teletv

import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ApoioActivity : AppCompatActivity() {
    companion object {
        const val BITCOIN = "14XJqVsMfVLpm6s4mX7mHNooihvtdfJq5J"
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(160, 40, 160, 40)
        }
        setContentView(raiz)
        fun texto(conteudo: String, tamanho: Float, cor: Int) = TextView(this).apply {
            text = conteudo
            textSize = tamanho
            gravity = Gravity.CENTER
            setTextColor(getColor(cor))
            setPadding(0, 12, 0, 12)
        }
        raiz.addView(texto(getString(R.string.support_title), 28f, R.color.destaque))
        raiz.addView(texto(getString(R.string.support_msg), 18f, R.color.texto))
        raiz.addView(ImageView(this).apply {
            setImageBitmap(Qr.gerar("bitcoin:$BITCOIN"))
            setBackgroundColor(getColor(android.R.color.white))
            setPadding(12, 12, 12, 12)
        }, LinearLayout.LayoutParams(420, 420).apply { topMargin = 16 })
        raiz.addView(texto(BITCOIN, 22f, R.color.texto))
        raiz.addView(texto(getString(R.string.support_hint), 15f, R.color.texto_fraco))
    }
}
