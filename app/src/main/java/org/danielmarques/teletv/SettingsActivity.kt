package org.danielmarques.teletv

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.drinkless.tdlib.TdApi

/** Menu principal com submenus; Voltar sobe um nível. */
class SettingsActivity : AppCompatActivity() {
    private enum class Tela { RAIZ, ARMAZENAMENTO, DOWNLOADS, SEGURANCA, ATUALIZACOES }

    private var tela = Tela.RAIZ
    private var origem = Tela.RAIZ
    private lateinit var cabecalho: TextView
    private lateinit var corpo: LinearLayout
    private var uso: TextView? = null
    private lateinit var ui: Linhas
    private val focos = HashMap<Tela, View>()
    private val aplicar = Runnable {
        Tg.aplicarLimites()
        Tg.limpar { atualizarUso() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(160, 40, 160, 40)
        }
        cabecalho = TextView(this).apply {
            textSize = 26f
            setTextColor(getColor(R.color.destaque))
            setPadding(0, 0, 0, 20)
        }
        corpo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        raiz.addView(cabecalho)
        raiz.addView(corpo)
        ui = Linhas(this, corpo)
        setContentView(ScrollView(this).apply { addView(raiz) })
    }

    override fun onResume() {
        super.onResume()
        // Refaz a tela ao voltar da senha ou de outra Activity, para refletir o que mudou.
        montar(tela)
    }

    override fun onStop() {
        super.onStop()
        corpo.removeCallbacks(aplicar)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (tela == Tela.RAIZ) super.onBackPressed() else montar(Tela.RAIZ)
    }

    private fun montar(nova: Tela) {
        origem = tela
        tela = nova
        ui.limpar()
        focos.clear()
        uso = null
        when (nova) {
            Tela.RAIZ -> montarRaiz()
            Tela.ARMAZENAMENTO -> montarArmazenamento()
            Tela.DOWNLOADS -> montarDownloads()
            Tela.SEGURANCA -> montarSeguranca()
            Tela.ATUALIZACOES -> montarAtualizacoes()
        }
        // Ao voltar para o menu, o foco retorna à entrada de onde se saiu.
        val alvo = (if (nova == Tela.RAIZ) focos[origem] else null) ?: ui.primeiro
        corpo.post { alvo?.requestFocus() }
    }

    private fun montarRaiz() {
        cabecalho.setText(R.string.settings)
        fun submenu(destino: Tela, titulo: Int, resumo: String) {
            focos[destino] = item(getString(titulo), resumo) { montar(destino) }
        }
        submenu(
            Tela.ARMAZENAMENTO, R.string.set_storage,
            getString(R.string.set_storage_sum, Formato.tamanho(Prefs.limiteBytes), idade(Prefs.dias)),
        )
        submenu(
            Tela.DOWNLOADS, R.string.set_downloads,
            getString(R.string.set_downloads_sum, Prefs.autoChats.size, Prefs.autoQtd),
        )
        submenu(Tela.SEGURANCA, R.string.set_security, "${getString(R.string.pin)}: ${ligado(Prefs.temSenha)}")
        submenu(Tela.ATUALIZACOES, R.string.set_updates, getString(R.string.set_updates_sum, BuildConfig.VERSION_NAME))
        item(
            getString(R.string.set_api),
            if (Prefs.apiId != 0) getString(R.string.set_api_own, Prefs.apiId) else getString(R.string.set_api_builtin),
        ) {
            Ponte.pedirApi = true
            startActivity(Intent(this, LoginActivity::class.java))
        }
        item(getString(R.string.support), getString(R.string.support_sum)) {
            startActivity(Intent(this, ApoioActivity::class.java))
        }
        item(getString(R.string.guide), getString(R.string.guide_sum)) {
            startActivity(Intent(this, GuiaActivity::class.java))
        }
        item(getString(R.string.about), null) { startActivity(Intent(this, SobreActivity::class.java)) }
        item(getString(R.string.logout), null) {
            AlertDialog.Builder(this)
                .setMessage(R.string.logout_confirm)
                .setPositiveButton(R.string.logout_yes) { _, _ ->
                    Tg.enviar(TdApi.LogOut())
                    finish()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun montarArmazenamento() {
        cabecalho.setText(R.string.set_storage)
        uso = nota("")
        deslizante(getString(R.string.max_space), Prefs.limitesGb, Prefs.limiteGb, { Formato.tamanho((it * 1073741824L).toLong()) }) {
            Prefs.limiteGb = it
            agendarLimpeza()
        }
        deslizante(getString(R.string.delete_after), Prefs.diasOpcoes, Prefs.dias, ::idade) {
            Prefs.dias = it
            agendarLimpeza()
        }
        item(getString(R.string.delete_all), null) { Tg.limpar(tudo = true) { atualizarUso() } }
        atualizarUso()
    }

    private fun montarDownloads() {
        cabecalho.setText(R.string.set_downloads)
        nota(getString(R.string.auto_note))
        chave(getString(R.string.auto_master), null, Prefs.autoLigado) { ligar ->
            Prefs.autoLigado = ligar
            if (ligar) AutoDownload.sincronizar()
        }
        item(getString(R.string.choose_chats), getString(R.string.choose_chats_sum, Prefs.autoChats.size)) {
            startActivity(Intent(this, AutoChatsActivity::class.java))
        }
        deslizante(getString(R.string.auto_count), Prefs.autoQtdOpcoes, Prefs.autoQtd, { it.toString() }) {
            Prefs.autoQtd = it
        }
    }

    private fun montarSeguranca() {
        cabecalho.setText(R.string.set_security)
        chave(getString(R.string.pin), getString(R.string.pin_note), Prefs.temSenha) { ligar ->
            // A senha só muda depois de digitada na tela própria; ao voltar, onResume refaz a chave.
            val modo = if (ligar) PinActivity.DEFINIR else PinActivity.REMOVER
            startActivity(Intent(this, PinActivity::class.java).putExtra("modo", modo))
        }
    }

    private fun montarAtualizacoes() {
        cabecalho.setText(R.string.set_updates)
        nota(getString(R.string.set_updates_sum, BuildConfig.VERSION_NAME))
        chave(getString(R.string.auto_update), null, Prefs.autoAtualizar) { Prefs.autoAtualizar = it }
        item(getString(R.string.check_update), null) { Atualizador.verificar(this, manual = true) }
    }

    private fun idade(dias: Int): String = when (dias) {
        0 -> getString(R.string.never)
        1 -> getString(R.string.day_one)
        else -> getString(R.string.days_many, dias)
    }

    private fun ligado(v: Boolean) = getString(if (v) R.string.on else R.string.off)

    private fun agendarLimpeza() {
        // Espera o controle parar de mexer antes de apagar arquivos.
        corpo.removeCallbacks(aplicar)
        corpo.postDelayed(aplicar, 1200)
    }

    private fun atualizarUso() {
        Tg.enviar(TdApi.GetStorageStatisticsFast()) { r ->
            if (r is TdApi.StorageStatisticsFast) {
                uso?.text = getString(R.string.usage, Formato.tamanho(r.filesSize), Formato.tamanho(filesDir.usableSpace))
            }
        }
    }

    private fun nota(conteudo: String) = ui.nota(conteudo)

    private fun item(titulo: String, resumo: String?, aoClicar: () -> Unit) = ui.item(titulo, resumo, aoClicar)

    private fun chave(titulo: String, resumo: String?, marcado: Boolean, aoMudar: (Boolean) -> Unit) =
        ui.chave(titulo, resumo, marcado, aoMudar)

    private fun <T> deslizante(titulo: String, valores: List<T>, atual: T, rotulo: (T) -> String, aoMudar: (T) -> Unit) =
        ui.deslizante(titulo, valores, atual, rotulo, aoMudar)
}
