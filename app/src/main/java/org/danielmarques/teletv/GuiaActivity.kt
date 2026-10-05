package org.danielmarques.teletv

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.drinkless.tdlib.TdApi

/** Guia de primeira execução: uma etapa por tela, terminando com as informações de doação. */
class GuiaActivity : AppCompatActivity() {
    private enum class Passo { BOAS_VINDAS, LOGIN, ARMAZENAMENTO, DOWNLOADS, SENHA, DICAS, FIM }

    private var passo = Passo.BOAS_VINDAS
    private var loginPedido = false
    private lateinit var etapa: TextView
    private lateinit var titulo: TextView
    private lateinit var corpo: LinearLayout
    private lateinit var botoes: LinearLayout
    private lateinit var ui: Linhas

    private val conectado get() = Tg.auth is TdApi.AuthorizationStateReady

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(200, 48, 200, 48)
        }
        etapa = TextView(this).apply {
            textSize = 15f
            setTextColor(getColor(R.color.texto_fraco))
        }
        titulo = TextView(this).apply {
            textSize = 30f
            setTextColor(getColor(R.color.destaque))
            setPadding(0, 4, 0, 16)
        }
        corpo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        botoes = LinearLayout(this).apply {
            gravity = Gravity.END
            setPadding(0, 20, 0, 0)
        }
        raiz.addView(etapa)
        raiz.addView(titulo)
        raiz.addView(corpo)
        raiz.addView(botoes)
        ui = Linhas(this, corpo)
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(raiz)
        })
        passo = Passo.values()[b?.getInt("passo") ?: 0]
    }

    override fun onSaveInstanceState(b: Bundle) {
        super.onSaveInstanceState(b)
        b.putInt("passo", passo.ordinal)
    }

    override fun onResume() {
        super.onResume()
        // Ao voltar do login já conectado, segue adiante sozinho.
        if (passo == Passo.LOGIN && loginPedido && conectado) passo = Passo.ARMAZENAMENTO
        loginPedido = false
        montar()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            passo != Passo.BOAS_VINDAS -> ir(Passo.values()[passo.ordinal - 1])
            // Sem concluir o guia não há tela atrás desta para mostrar.
            Prefs.guiaFeito -> finish()
            else -> finishAffinity()
        }
    }

    private fun ir(novo: Passo) {
        passo = novo
        montar()
    }

    private fun montar() {
        ui.limpar()
        botoes.removeAllViews()
        etapa.text = getString(R.string.guide_step, passo.ordinal + 1, Passo.values().size)
        var avancar = getString(R.string.guide_next)
        var aoAvancar: () -> Unit = { ir(Passo.values()[passo.ordinal + 1]) }
        when (passo) {
            Passo.BOAS_VINDAS -> {
                pagina(R.string.g_welcome_t, R.string.g_welcome)
                botao(getString(R.string.guide_skip)) { ir(Passo.FIM) }
            }
            Passo.LOGIN -> {
                pagina(R.string.g_login_t, R.string.g_login)
                if (conectado) {
                    ui.nota(getString(R.string.g_login_done))
                } else {
                    avancar = getString(R.string.g_login_go)
                    aoAvancar = {
                        loginPedido = true
                        startActivity(Intent(this, LoginActivity::class.java))
                    }
                }
            }
            Passo.ARMAZENAMENTO -> {
                pagina(R.string.g_storage_t, R.string.g_storage)
                ui.deslizante(getString(R.string.max_space), Prefs.limitesGb, Prefs.limiteGb, { Formato.tamanho((it * 1073741824L).toLong()) }) {
                    Prefs.limiteGb = it
                }
                ui.deslizante(getString(R.string.delete_after), Prefs.diasOpcoes, Prefs.dias, ::idade) { Prefs.dias = it }
                // Só aplica ao sair da etapa, para não apagar nada enquanto a barra ainda se move.
                val seguir = aoAvancar
                aoAvancar = {
                    Tg.aplicarLimites()
                    Tg.limpar()
                    seguir()
                }
            }
            Passo.DOWNLOADS -> {
                pagina(R.string.g_auto_t, R.string.g_auto)
                ui.item(getString(R.string.choose_chats), resources.getQuantityString(R.plurals.choose_chats_sum, Prefs.autoChats.size, Prefs.autoChats.size)) {
                    startActivity(Intent(this, AutoChatsActivity::class.java))
                }
            }
            Passo.SENHA -> {
                pagina(R.string.g_pin_t, R.string.g_pin)
                if (Prefs.temSenha) {
                    ui.nota(getString(R.string.g_pin_done))
                } else {
                    ui.item(getString(R.string.g_pin_create), getString(R.string.pin_note)) {
                        startActivity(Intent(this, PinActivity::class.java).putExtra("modo", PinActivity.DEFINIR))
                    }
                }
            }
            Passo.DICAS -> pagina(R.string.g_tips_t, R.string.g_tips)
            Passo.FIM -> {
                pagina(R.string.g_done_t, R.string.g_done)
                val linha = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                linha.addView(ImageView(this).apply {
                    setImageBitmap(Qr.gerar("bitcoin:${ApoioActivity.BITCOIN}", 440))
                    setBackgroundColor(getColor(android.R.color.white))
                    setPadding(10, 10, 10, 10)
                }, LinearLayout.LayoutParams(300, 300))
                val lado = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(40, 0, 0, 0)
                }
                lado.addView(ui.texto("Bitcoin", 16f, R.color.texto_fraco))
                lado.addView(ui.texto(ApoioActivity.BITCOIN, 21f, R.color.texto))
                lado.addView(ui.texto(getString(R.string.support_hint), 15f, R.color.texto_fraco).apply { setPadding(0, 12, 0, 0) })
                linha.addView(lado)
                corpo.addView(linha)
                avancar = getString(R.string.guide_finish)
                aoAvancar = {
                    Prefs.guiaFeito = true
                    finish()
                }
            }
        }
        if (passo != Passo.BOAS_VINDAS) botao(getString(R.string.guide_back)) { ir(Passo.values()[passo.ordinal - 1]) }
        val principal = botao(avancar, aoAvancar)
        // As barras e os itens ficam acima; o foco começa no botão que leva adiante.
        principal.post { principal.requestFocus() }
    }

    private fun pagina(tituloId: Int, textoId: Int) {
        titulo.setText(tituloId)
        corpo.addView(ui.texto(getString(textoId), 19f, R.color.texto).apply {
            setLineSpacing(0f, 1.3f)
            setPadding(0, 0, 0, 24)
        })
    }

    private fun botao(rotulo: String, aoClicar: () -> Unit): Button {
        val bt = Button(this).apply {
            text = rotulo
            isAllCaps = false
            textSize = 18f
            setTextColor(getColor(R.color.texto))
            setBackgroundResource(R.drawable.foco)
            setPadding(40, 0, 40, 0)
            setOnClickListener { aoClicar() }
        }
        botoes.addView(bt, LinearLayout.LayoutParams(-2, -2).apply { marginStart = 16 })
        return bt
    }

    private fun idade(dias: Int): String = when (dias) {
        0 -> getString(R.string.never)
        1 -> getString(R.string.day_one)
        else -> getString(R.string.days_many, dias)
    }
}
