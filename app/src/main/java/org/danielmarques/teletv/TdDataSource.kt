package org.danielmarques.teletv

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.io.RandomAccessFile

/** Lê o vídeo direto do arquivo parcial da TDLib, pedindo o trecho que o player quer. */
@OptIn(UnstableApi::class)
class TdDataSource(private val idArquivo: Int, private val tamanho: Long) : BaseDataSource(true) {
    private var uri: Uri? = null
    private var raf: RandomAccessFile? = null
    private var posicao = 0L
    private var restante = 0L
    private var aberto = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        posicao = dataSpec.position
        restante = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else tamanho - posicao
        if (!completo()) Tg.baixar(idArquivo, 32, posicao)
        aberto = true
        transferStarted(dataSpec)
        return restante
    }

    private fun completo() = Tg.arquivos[idArquivo]?.local?.isDownloadingCompleted == true

    private fun disponivel(): Long {
        val l = Tg.arquivos[idArquivo]?.local ?: return 0
        if (l.path.isEmpty()) return 0
        if (l.isDownloadingCompleted) return tamanho - posicao
        val fim = l.downloadOffset + l.downloadedPrefixSize
        return if (posicao >= l.downloadOffset && posicao < fim) fim - posicao else 0
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (restante <= 0) return C.RESULT_END_OF_INPUT
        var disp = disponivel()
        var esperas = 0
        while (disp <= 0) {
            if (Thread.currentThread().isInterrupted) throw IOException("read interrupted")
            synchronized(Tg.travaArquivos) {
                try {
                    Tg.travaArquivos.wait(500)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("read interrupted")
                }
            }
            disp = disponivel()
            // Sem atividade por 10 s: o download pode ter sido cancelado; pede de novo.
            if (disp <= 0 && ++esperas % 20 == 0 && Tg.arquivos[idArquivo]?.local?.isDownloadingActive != true) {
                Tg.baixar(idArquivo, 32, posicao)
            }
        }
        val arq = raf ?: RandomAccessFile(Tg.arquivos[idArquivo]!!.local.path, "r").also { raf = it }
        arq.seek(posicao)
        val n = arq.read(buffer, offset, minOf(length.toLong(), disp, restante).toInt())
        if (n <= 0) return 0
        posicao += n
        restante -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        raf?.close()
        raf = null
        if (aberto) {
            aberto = false
            transferEnded()
        }
    }
}
