package org.danielmarques.teletv

import org.drinkless.tdlib.TdApi

/**
 * Baixa sozinho os vídeos recentes das conversas marcadas, um por vez e só
 * enquanto couber no limite de espaço. Roda apenas com o app aberto.
 * Todos os métodos são chamados na thread principal.
 */
object AutoDownload {
    private val fila = ArrayDeque<Pair<String, TdApi.File>>()
    private var atual = 0
    private var chaveAtual = ""

    fun sincronizar() {
        for (chat in Prefs.autoChats) {
            val busca = TdApi.SearchChatMessages(chat, null, "", null, 0, 0, Prefs.autoQtd, TdApi.SearchMessagesFilterVideo())
            Tg.enviar(busca) { r ->
                if (r is TdApi.FoundChatMessages) {
                    r.messages.forEach(::enfileirar)
                    proximo()
                }
            }
        }
    }

    fun aoChegar(m: TdApi.Message) {
        if (m.chatId !in Prefs.autoChats) return
        enfileirar(m)
        proximo()
    }

    fun aoAtualizarArquivo(f: TdApi.File) {
        if (f.id != atual) return
        if (f.local.isDownloadingCompleted) Prefs.marcarAutoFeito(chaveAtual)
        // Terminou, falhou ou foi cancelado: segue para o próximo da fila.
        if (f.local.isDownloadingCompleted || !f.local.isDownloadingActive) {
            atual = 0
            proximo()
        }
    }

    private fun enfileirar(m: TdApi.Message) {
        val video = (m.content as? TdApi.MessageVideo)?.video?.video ?: return
        val chave = "${m.chatId}_${m.id}"
        if (Prefs.autoFeito(chave) || chave == chaveAtual || fila.any { it.first == chave }) return
        if (Tg.arquivo(video).local.isDownloadingCompleted) return
        fila.addLast(chave to video)
    }

    private fun proximo() {
        if (atual != 0) return
        val (chave, video) = fila.removeFirstOrNull() ?: return
        val f = Tg.arquivo(video)
        val falta = (if (f.size > 0) f.size else f.expectedSize) - f.local.downloadedSize
        Tg.enviar(TdApi.GetStorageStatisticsFast()) { r ->
            val uso = (r as? TdApi.StorageStatisticsFast)?.filesSize ?: 0
            val livre = Tg.espacoLivre()
            if (atual != 0) {
                fila.addFirst(chave to video)
            } else if (uso + falta > Prefs.limiteBytes || falta > livre - 500L * 1048576) {
                // Não cabe: pula este e tenta os seguintes, que podem ser menores.
                proximo()
            } else {
                atual = f.id
                chaveAtual = chave
                Tg.baixar(f.id, 1)
            }
        }
    }
}
