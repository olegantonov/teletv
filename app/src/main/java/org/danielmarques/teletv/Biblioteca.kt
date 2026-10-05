package org.danielmarques.teletv

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Histórico do que foi assistido e favoritos (vídeos e conversas), guardados no aparelho. */
object Biblioteca {
    class Entrada(val chat: Long, val msg: Long, val titulo: String, val pos: Long, val dur: Long, val quando: Long) {
        val chave get() = "${chat}_$msg"

        /** Parou no meio: aparece em "continuar assistindo". */
        val emAndamento get() = pos > 0
    }

    private const val MAXIMO = 200
    private lateinit var sp: SharedPreferences
    private var historicoCache: List<Entrada>? = null
    private var favoritosCache: List<Entrada>? = null

    fun iniciar(ctx: Context) {
        sp = ctx.getSharedPreferences("biblioteca", Context.MODE_PRIVATE)
    }

    private fun ler(chave: String): List<Entrada> {
        val a = JSONArray(sp.getString(chave, "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Entrada(o.getLong("c"), o.getLong("m"), o.optString("t"), o.optLong("p"), o.optLong("d"), o.optLong("q"))
        }
    }

    private fun gravar(chave: String, lista: List<Entrada>) {
        val a = JSONArray()
        lista.forEach {
            a.put(JSONObject().put("c", it.chat).put("m", it.msg).put("t", it.titulo).put("p", it.pos).put("d", it.dur).put("q", it.quando))
        }
        sp.edit().putString(chave, a.toString()).apply()
    }

    /** Do mais recente para o mais antigo. */
    val historico: List<Entrada> get() = historicoCache ?: ler("historico").also { historicoCache = it }

    val continuar: List<Entrada> get() = historico.filter { it.emAndamento }

    val videosFavoritos: List<Entrada> get() = favoritosCache ?: ler("videos_favoritos").also { favoritosCache = it }

    /** [pos] 0 significa assistido até o fim. */
    fun registrar(chat: Long, msg: Long, titulo: String, pos: Long, dur: Long) {
        val nova = Entrada(chat, msg, titulo, pos, dur, System.currentTimeMillis())
        val lista = (listOf(nova) + historico.filterNot { it.chave == nova.chave }).take(MAXIMO)
        historicoCache = lista
        gravar("historico", lista)
    }

    fun removerDoHistorico(chave: String) {
        val lista = historico.filterNot { it.chave == chave }
        historicoCache = lista
        gravar("historico", lista)
        Prefs.salvarPosicao(chave, 0)
    }

    fun noHistorico(chave: String) = historico.any { it.chave == chave }

    /** Quanto já foi visto, de 0 a 1000; 0 quando nunca aberto ou já concluído. */
    fun progresso(chave: String): Int {
        val e = historico.firstOrNull { it.chave == chave } ?: return 0
        return if (e.dur > 0) (e.pos * 1000 / e.dur).toInt().coerceIn(0, 1000) else 0
    }

    fun videoFavorito(chave: String) = videosFavoritos.any { it.chave == chave }

    /** Devolve true se passou a ser favorito. */
    fun alternarVideo(chat: Long, msg: Long, titulo: String): Boolean {
        val chave = "${chat}_$msg"
        val era = videoFavorito(chave)
        val lista = if (era) videosFavoritos.filterNot { it.chave == chave }
        else listOf(Entrada(chat, msg, titulo, 0, 0, System.currentTimeMillis())) + videosFavoritos
        favoritosCache = lista
        gravar("videos_favoritos", lista)
        return !era
    }

    val chatsFavoritos: Set<Long>
        get() = sp.getStringSet("chats_favoritos", emptySet())!!.map { it.toLong() }.toSet()

    fun alternarChat(id: Long): Boolean {
        val atual = sp.getStringSet("chats_favoritos", emptySet())!!.toMutableSet()
        val era = id.toString() in atual
        if (era) atual -= id.toString() else atual += id.toString()
        sp.edit().putStringSet("chats_favoritos", atual).apply()
        return !era
    }
}
