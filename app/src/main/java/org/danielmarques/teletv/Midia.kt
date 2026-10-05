package org.danielmarques.teletv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import org.drinkless.tdlib.TdApi
import java.util.concurrent.Executors

/** Um vídeo de uma mensagem do Telegram, pronto para aparecer num cartão e ser tocado. */
class Video(
    val chat: Long,
    val msg: Long,
    val arquivo: TdApi.File,
    val titulo: String,
    val duracao: Int,
    val capa: TdApi.Thumbnail?,
    val mini: ByteArray?,
    val data: Int,
) {
    val chave get() = "${chat}_$msg"
}

object Midia {
    private val extensoes = setOf("mkv", "mp4", "avi", "mov", "webm", "ts", "m4v")

    fun tamanho(f: TdApi.File) = if (f.size > 0) f.size else f.expectedSize

    /** Null quando a mensagem não traz vídeo (nem arquivo de vídeo enviado como documento). */
    fun deMensagem(ctx: Context, m: TdApi.Message): Video? = when (val c = m.content) {
        is TdApi.MessageVideo -> Video(
            m.chatId, m.id, c.video.video,
            c.caption.text.ifBlank { c.video.fileName }.ifBlank { ctx.getString(R.string.video_default) },
            c.video.duration, c.video.thumbnail, c.video.minithumbnail?.data, m.date,
        )
        is TdApi.MessageDocument -> {
            val d = c.document
            val ehVideo = d.mimeType.startsWith("video/") || d.fileName.substringAfterLast('.', "").lowercase() in extensoes
            if (!ehVideo) null else Video(
                m.chatId, m.id, d.document,
                d.fileName.ifBlank { c.caption.text }.ifBlank { ctx.getString(R.string.video_default) },
                0, d.thumbnail, d.minithumbnail?.data, m.date,
            )
        }
        else -> null
    }

    /** Busca as mensagens uma a uma, na ordem dada; as que sumiram do Telegram ficam de fora. */
    fun resolver(ctx: Context, entradas: List<Biblioteca.Entrada>, aoTerminar: (List<Video>) -> Unit) {
        val achados = ArrayList<Video>()
        fun proxima(i: Int) {
            if (i >= entradas.size) return aoTerminar(achados)
            Tg.enviar(TdApi.GetMessage(entradas[i].chat, entradas[i].msg)) { r ->
                if (r is TdApi.Message) deMensagem(ctx, r)?.let(achados::add)
                proxima(i + 1)
            }
        }
        proxima(0)
    }

    /** Abre o player; antes libera espaço para o que já está baixado mais este vídeo caber no limite. */
    fun abrir(a: Activity, v: Video, aoPreparar: (Boolean) -> Unit = {}) {
        val f = Tg.arquivo(v.arquivo)
        val total = tamanho(f)
        val falta = total - f.local.downloadedSize
        if (f.local.isDownloadingCompleted) return tocar(a, v, total)
        aoPreparar(true)
        Tg.limpar(reservar = falta) { _ ->
            val livre = a.filesDir.usableSpace
            if (falta > livre - 300L * 1048576) {
                aviso(a, a.getString(R.string.no_space, Formato.tamanho(total), Formato.tamanho(livre)))
            } else {
                if (total > Prefs.limiteBytes) aviso(a, a.getString(R.string.over_limit, Formato.tamanho(Prefs.limiteBytes)))
                tocar(a, v, total)
            }
            aoPreparar(false)
        }
    }

    private fun aviso(a: Activity, texto: String) = Toast.makeText(a, texto, Toast.LENGTH_LONG).show()

    private fun tocar(a: Activity, v: Video, total: Long) {
        a.startActivity(
            Intent(a, PlayerActivity::class.java)
                .putExtra("arquivo", v.arquivo.id)
                .putExtra("tamanho", total)
                .putExtra("chat", v.chat)
                .putExtra("msg", v.msg)
                .putExtra("titulo", v.titulo)
        )
    }
}

/** Carrega e guarda em memória as capas dos vídeos de uma tela. */
class Capas(private val a: Activity) {
    private val cache = LruCache<Int, Bitmap>(120)
    private val decodificador = Executors.newSingleThreadExecutor()

    fun carregar(v: Video, destino: ImageView) {
        val capa = v.capa
        destino.tag = v.chave
        cache.get(capa?.file?.id ?: -1)?.let { return destino.setImageBitmap(it) }
        destino.setImageBitmap(v.mini?.let { d -> BitmapFactory.decodeByteArray(d, 0, d.size) })
        if (capa == null) return
        val f = Tg.arquivo(capa.file)
        // A tela é avisada pelo UpdateFile quando o download da capa terminar e refaz o cartão.
        if (!f.local.isDownloadingCompleted) return Tg.baixar(f.id, 1)
        decodificador.execute {
            val bmp = BitmapFactory.decodeFile(f.local.path) ?: return@execute
            cache.put(f.id, bmp)
            a.runOnUiThread { if (destino.tag == v.chave) destino.setImageBitmap(bmp) }
        }
    }

    fun encerrar() {
        decodificador.shutdownNow()
    }
}

class CartaoVideo(v: View) : RecyclerView.ViewHolder(v) {
    private val capa: ImageView = v.findViewById(R.id.capa)
    private val duracao: TextView = v.findViewById(R.id.duracao)
    private val nome: TextView = v.findViewById(R.id.nome)
    private val meta: TextView = v.findViewById(R.id.meta)
    private val progresso: ProgressBar = v.findViewById(R.id.progresso)

    fun mostrar(v: Video, capas: Capas) {
        val ctx = itemView.context
        val f = Tg.arquivo(v.arquivo)
        val total = Midia.tamanho(f)
        nome.text = if (Biblioteca.videoFavorito(v.chave)) ctx.getString(R.string.chat_fav, v.titulo) else v.titulo
        duracao.text = if (v.duracao > 0) Formato.duracao(v.duracao) else ""
        duracao.visibility = if (v.duracao > 0) View.VISIBLE else View.GONE
        val base = "${Formato.tamanho(total)} · ${Formato.data(v.data)}"
        meta.text = when {
            f.local.isDownloadingCompleted -> ctx.getString(R.string.meta_downloaded, base)
            f.local.downloadedSize > 0 && total > 0 ->
                ctx.getString(R.string.meta_downloading, base, (f.local.downloadedSize * 100 / total).toInt())
            else -> base
        }
        val visto = Biblioteca.progresso(v.chave)
        progresso.progress = visto
        progresso.visibility = if (visto > 0) View.VISIBLE else View.INVISIBLE
        capas.carregar(v, capa)
    }
}

/** Menu de um vídeo aberto ao segurar OK: favoritos, apagar download, tirar do histórico. */
object MenuVideo {
    fun abrir(a: Activity, v: Video, aoMudar: () -> Unit) {
        val opcoes = ArrayList<Pair<String, () -> Unit>>()
        val favorito = Biblioteca.videoFavorito(v.chave)
        opcoes += a.getString(if (favorito) R.string.fav_remove else R.string.fav_add) to {
            Biblioteca.alternarVideo(v.chat, v.msg, v.titulo)
            aoMudar()
        }
        val f = Tg.arquivo(v.arquivo)
        if (f.local.downloadedSize > 0) {
            opcoes += a.getString(R.string.del_download) to {
                Tg.enviar(TdApi.DeleteFile(f.id)) { _ ->
                    Toast.makeText(a, R.string.download_deleted, Toast.LENGTH_SHORT).show()
                    aoMudar()
                }
            }
        }
        if (Biblioteca.noHistorico(v.chave)) {
            opcoes += a.getString(R.string.hist_remove) to {
                Biblioteca.removerDoHistorico(v.chave)
                aoMudar()
            }
        }
        androidx.appcompat.app.AlertDialog.Builder(a)
            .setTitle(v.titulo.take(60))
            .setItems(opcoes.map { it.first }.toTypedArray()) { _, i -> opcoes[i].second() }
            .show()
    }
}
