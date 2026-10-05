package org.danielmarques.teletv

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.drinkless.tdlib.TdApi

class SettingsActivity : AppCompatActivity() {
    private lateinit var limite: TextView
    private lateinit var idade: TextView
    private lateinit var uso: TextView
    private lateinit var auto: TextView
    private lateinit var senha: TextView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(160, 40, 160, 40)
        }
        setContentView(ScrollView(this).apply { addView(raiz) })
        raiz.addView(TextView(this).apply {
            text = "Configurações"
            textSize = 26f
            setTextColor(getColor(R.color.destaque))
        })
        uso = TextView(this).apply {
            textSize = 16f
            setTextColor(getColor(R.color.texto_fraco))
            setPadding(0, 16, 0, 24)
        }
        raiz.addView(uso)

        limite = linha(raiz) {
            Prefs.limiteGb = seguinte(Prefs.limitesGb.toList(), Prefs.limiteGb)
            aoMudar()
        }
        idade = linha(raiz) {
            Prefs.dias = seguinte(Prefs.diasOpcoes.toList(), Prefs.dias)
            aoMudar()
        }
        auto = linha(raiz) {
            Prefs.autoQtd = seguinte(Prefs.autoQtdOpcoes.toList(), Prefs.autoQtd)
            mostrar()
            AutoDownload.sincronizar()
        }
        senha = linha(raiz) {
            val modo = if (Prefs.temSenha) PinActivity.REMOVER else PinActivity.DEFINIR
            startActivity(Intent(this, PinActivity::class.java).putExtra("modo", modo))
        }
        linha(raiz) {
            Tg.limpar(tudo = true) { atualizarUso() }
        }.text = "Apagar agora todos os vídeos baixados"
        linha(raiz) { Atualizador.verificar(this, manual = true) }.text =
            "Procurar atualização (versão instalada: ${BuildConfig.VERSION_NAME})"
        linha(raiz) { startActivity(Intent(this, ApoioActivity::class.java)) }.text = "Apoiar o projeto (doação em Bitcoin)"
        linha(raiz) { startActivity(Intent(this, SobreActivity::class.java)) }.text = "Sobre e licenças"
        linha(raiz) {
            AlertDialog.Builder(this)
                .setMessage("Sair da conta do Telegram neste aparelho?")
                .setPositiveButton("Sair") { _, _ ->
                    Tg.enviar(TdApi.LogOut())
                    finish()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }.text = "Sair da conta do Telegram"

        limite.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        mostrar()
    }

    private fun <T> seguinte(opcoes: List<T>, atual: T): T = opcoes[(opcoes.indexOf(atual) + 1) % opcoes.size]

    private fun linha(raiz: LinearLayout, aoClicar: () -> Unit): TextView {
        val t = TextView(this).apply {
            textSize = 20f
            setTextColor(getColor(R.color.texto))
            setBackgroundResource(R.drawable.foco)
            setPadding(32, 18, 32, 18)
            isFocusable = true
            isClickable = true
            setOnClickListener { aoClicar() }
        }
        raiz.addView(t, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        return t
    }

    private fun aoMudar() {
        mostrar()
        Tg.aplicarLimites()
        Tg.limpar { atualizarUso() }
    }

    private fun mostrar() {
        limite.text = "Espaço máximo para vídeos baixados: ${Formato.tamanho(Prefs.limiteBytes)}"
        idade.text = "Apagar vídeos não abertos há: " +
            if (Prefs.dias == 0) "nunca apagar por idade" else if (Prefs.dias == 1) "1 dia" else "${Prefs.dias} dias"
        val marcadas = Prefs.autoChats.size
        auto.text = "Download automático: os ${Prefs.autoQtd} vídeos mais recentes de cada conversa marcada ($marcadas marcadas)"
        senha.text = if (Prefs.temSenha) "Senha do app: ativada (clique para remover)" else "Senha do app: desativada (clique para criar)"
        atualizarUso()
    }

    private fun atualizarUso() {
        val livre = Formato.tamanho(filesDir.usableSpace)
        uso.text = "Livre no aparelho: $livre"
        Tg.enviar(TdApi.GetStorageStatisticsFast()) { r ->
            if (r is TdApi.StorageStatisticsFast) {
                uso.text = "Em uso pelo TeleTV: ${Formato.tamanho(r.filesSize)} · Livre no aparelho: ${Formato.tamanho(filesDir.usableSpace)}"
            }
        }
    }
}
