package org.danielmarques.teletv

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat

/** Linhas de menu para o controle remoto: item, chave e barra deslizante, empilhadas em [corpo]. */
class Linhas(private val a: Activity, private val corpo: LinearLayout) {
    /** Primeira linha focável criada desde a última limpeza; quem monta a tela decide se foca nela. */
    var primeiro: View? = null

    fun limpar() {
        corpo.removeAllViews()
        primeiro = null
    }

    fun texto(conteudo: String, tamanho: Float, cor: Int) = TextView(a).apply {
        text = conteudo
        textSize = tamanho
        setTextColor(a.getColor(cor))
    }

    private fun linha(): LinearLayout {
        val l = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.foco)
            setPadding(32, 18, 32, 18)
        }
        corpo.addView(l, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        return l
    }

    fun nota(conteudo: String): TextView {
        val t = texto(conteudo, 16f, R.color.texto_fraco).apply { setPadding(0, 4, 0, 14) }
        corpo.addView(t)
        return t
    }

    fun item(titulo: String, resumo: String?, aoClicar: () -> Unit): View {
        val l = linha()
        l.addView(texto(titulo, 20f, R.color.texto))
        if (resumo != null) l.addView(texto(resumo, 15f, R.color.texto_fraco))
        l.isFocusable = true
        l.isClickable = true
        l.setOnClickListener { aoClicar() }
        if (primeiro == null) primeiro = l
        return l
    }

    fun chave(titulo: String, resumo: String?, marcado: Boolean, aoMudar: (Boolean) -> Unit) {
        val l = linha()
        val topo = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        val interruptor = SwitchCompat(a).apply {
            isChecked = marcado
            isFocusable = false
            isClickable = false
        }
        topo.addView(texto(titulo, 20f, R.color.texto), LinearLayout.LayoutParams(0, -2, 1f))
        topo.addView(interruptor)
        l.addView(topo)
        if (resumo != null) l.addView(texto(resumo, 15f, R.color.texto_fraco))
        l.isFocusable = true
        l.isClickable = true
        l.setOnClickListener {
            interruptor.isChecked = !interruptor.isChecked
            aoMudar(interruptor.isChecked)
        }
        if (primeiro == null) primeiro = l
    }

    /** Barra deslizante sobre uma lista de valores: esquerda e direita do controle mudam o valor. */
    fun <T> deslizante(titulo: String, valores: List<T>, atual: T, rotulo: (T) -> String, aoMudar: (T) -> Unit) {
        val l = linha()
        val topo = LinearLayout(a)
        val valor = texto(rotulo(atual), 20f, R.color.destaque)
        topo.addView(texto(titulo, 20f, R.color.texto), LinearLayout.LayoutParams(0, -2, 1f))
        topo.addView(valor)
        l.addView(topo)
        val barra = SeekBar(a).apply {
            max = valores.size - 1
            progress = valores.indexOf(atual).coerceAtLeast(0)
            keyProgressIncrement = 1
            setPadding(8, 20, 8, 8)
            setOnFocusChangeListener { _, focado -> l.isSelected = focado }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, posicao: Int, doUsuario: Boolean) {
                    if (!doUsuario) return
                    valor.text = rotulo(valores[posicao])
                    aoMudar(valores[posicao])
                }

                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        l.addView(barra)
        if (primeiro == null) primeiro = barra
    }
}
