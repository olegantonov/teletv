package org.danielmarques.teletv

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

object Prefs {
    /** De 0,5 a 8 GB, de meio em meio. */
    val limitesGb = List(16) { (it + 1) * 0.5f }

    // 0 = nunca apagar por idade
    val diasOpcoes = listOf(1, 2, 3, 5, 7, 14, 30, 60, 0)

    private lateinit var sp: SharedPreferences

    fun iniciar(ctx: Context) {
        sp = ctx.getSharedPreferences("teletv", Context.MODE_PRIVATE)
    }

    var limiteGb: Float
        get() = sp.getFloat("limite_gb", 2f)
        set(v) = sp.edit().putFloat("limite_gb", v).apply()

    var dias: Int
        get() = sp.getInt("dias", 7)
        set(v) = sp.edit().putInt("dias", v).apply()

    val limiteBytes: Long get() = (limiteGb * 1024 * 1024 * 1024).toLong()

    val autoQtdOpcoes = (1..20).toList()

    /** Quantos vídeos recentes baixar sozinho em cada conversa marcada. */
    var autoQtd: Int
        get() = sp.getInt("auto_qtd", 5)
        set(v) = sp.edit().putInt("auto_qtd", v).apply()

    /** Chave geral: desligada, nenhuma conversa baixa sozinha, sem perder a seleção. */
    var autoLigado: Boolean
        get() = sp.getBoolean("auto_ligado", true)
        set(v) = sp.edit().putBoolean("auto_ligado", v).apply()

    val autoChats: Set<Long>
        get() = sp.getStringSet("auto_chats", emptySet())!!.map { it.toLong() }.toSet()

    fun definirAuto(chat: Long, ligado: Boolean) {
        val atual = sp.getStringSet("auto_chats", emptySet())!!.toMutableSet()
        if (ligado) atual += chat.toString() else atual -= chat.toString()
        sp.edit().putStringSet("auto_chats", atual).apply()
    }

    // Vídeos que o download automático já trouxe uma vez: não baixa de novo depois de apagados.
    fun autoFeito(chave: String): Boolean = sp.getStringSet("auto_feitos", emptySet())!!.contains(chave)

    fun marcarAutoFeito(chave: String) {
        val atual = sp.getStringSet("auto_feitos", emptySet())!!.toMutableList()
        atual += chave
        sp.edit().putStringSet("auto_feitos", atual.takeLast(1000).toSet()).apply()
    }

    var autoAtualizar: Boolean
        get() = sp.getBoolean("auto_atualizar", true)
        set(v) = sp.edit().putBoolean("auto_atualizar", v).apply()

    /** Chave de API informada pelo usuário; 0 = usar a que vem no APK. */
    val apiId: Int get() = sp.getInt("api_id", 0)
    val apiHash: String get() = sp.getString("api_hash", "") ?: ""

    fun definirApi(id: Int, hash: String) {
        sp.edit().putInt("api_id", id).putString("api_hash", hash).apply()
    }

    /** O guia de primeira execução já foi concluído ou pulado. */
    var guiaFeito: Boolean
        get() = sp.getBoolean("guia_feito", false)
        set(v) = sp.edit().putBoolean("guia_feito", v).apply()

    var ultimaVerificacao: Long
        get() = sp.getLong("ultima_verificacao", 0)
        set(v) = sp.edit().putLong("ultima_verificacao", v).apply()

    val temSenha: Boolean get() = sp.contains("senha")

    fun senhaConfere(pin: String): Boolean = sp.getString("senha", null) == resumo(pin)

    fun definirSenha(pin: String?) {
        sp.edit().apply { if (pin == null) remove("senha") else putString("senha", resumo(pin)) }.apply()
    }

    private fun resumo(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("teletv:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    fun posicao(chave: String): Long = sp.getLong("pos_$chave", 0)

    fun salvarPosicao(chave: String, ms: Long) {
        sp.edit().apply { if (ms > 0) putLong("pos_$chave", ms) else remove("pos_$chave") }.apply()
    }
}
