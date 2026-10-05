package org.danielmarques.teletv

import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PinActivity : AppCompatActivity() {
    companion object {
        const val ABRIR = 0
        const val DEFINIR = 1
        const val REMOVER = 2
        private const val TAMANHO = 4
    }

    private var modo = ABRIR
    private var digitado = ""
    private var primeira: String? = null
    private lateinit var titulo: TextView
    private lateinit var pontos: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        modo = intent.getIntExtra("modo", ABRIR)
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        setContentView(raiz)
        titulo = TextView(this).apply {
            textSize = 24f
            setTextColor(getColor(R.color.texto))
        }
        pontos = TextView(this).apply {
            textSize = 40f
            setTextColor(getColor(R.color.destaque))
            setPadding(0, 16, 0, 24)
        }
        raiz.addView(titulo, LinearLayout.LayoutParams(-2, -2))
        raiz.addView(pontos, LinearLayout.LayoutParams(-2, -2))

        val teclado = GridLayout(this).apply { columnCount = 3 }
        for (rotulo in listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "Apagar", "0")) {
            val bt = Button(this).apply {
                text = rotulo
                textSize = 22f
                isAllCaps = false
                setTextColor(getColor(R.color.texto))
                setBackgroundResource(R.drawable.foco)
                setOnClickListener { if (rotulo == "Apagar") apagar() else digitar(rotulo) }
            }
            teclado.addView(bt, GridLayout.LayoutParams().apply {
                width = 220
                height = 110
                setMargins(8, 8, 8, 8)
            })
            if (rotulo == "5") bt.post { bt.requestFocus() }
        }
        raiz.addView(teclado, LinearLayout.LayoutParams(-2, -2))
        mostrar()
    }

    override fun onKeyDown(codigo: Int, e: KeyEvent): Boolean {
        if (codigo in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            digitar((codigo - KeyEvent.KEYCODE_0).toString())
            return true
        }
        if (codigo == KeyEvent.KEYCODE_DEL) {
            apagar()
            return true
        }
        return super.onKeyDown(codigo, e)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Sem a senha não se volta para o app: fecha tudo.
        if (modo == ABRIR) finishAffinity() else finish()
    }

    private fun mostrar(aviso: String? = null) {
        titulo.text = aviso ?: when {
            modo == ABRIR -> "Digite a senha do TeleTV"
            modo == REMOVER -> "Digite a senha atual para removê-la"
            primeira == null -> "Crie uma senha de $TAMANHO dígitos"
            else -> "Repita a senha para confirmar"
        }
        pontos.text = "●".repeat(digitado.length) + "○".repeat(TAMANHO - digitado.length)
    }

    private fun apagar() {
        digitado = digitado.dropLast(1)
        mostrar()
    }

    private fun digitar(d: String) {
        if (digitado.length >= TAMANHO) return
        digitado += d
        mostrar()
        if (digitado.length == TAMANHO) pontos.postDelayed(::concluir, 150)
    }

    private fun concluir() {
        val pin = digitado
        digitado = ""
        when (modo) {
            ABRIR, REMOVER -> {
                if (!Prefs.senhaConfere(pin)) return mostrar("Senha incorreta. Tente de novo")
                if (modo == REMOVER) Prefs.definirSenha(null)
                Trava.liberado = true
                finish()
            }
            DEFINIR -> {
                val anterior = primeira
                if (anterior == null) {
                    primeira = pin
                    mostrar()
                } else if (anterior == pin) {
                    Prefs.definirSenha(pin)
                    Trava.liberado = true
                    finish()
                } else {
                    primeira = null
                    mostrar("As senhas não conferem. Crie de novo")
                }
            }
        }
    }
}
