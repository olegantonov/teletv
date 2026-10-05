package org.danielmarques.teletv

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/** Cliente único da TDLib: estado de login, chats e arquivos em download. */
object Tg {
    val principal = Handler(Looper.getMainLooper())
    val chats = ConcurrentHashMap<Long, TdApi.Chat>()
    val arquivos = ConcurrentHashMap<Int, TdApi.File>()
    val travaArquivos = Object()

    @Volatile var auth: TdApi.AuthorizationState? = null
    @Volatile var pastas: Array<TdApi.ChatFolderInfo> = emptyArray()
    @Volatile var loginAberto = false

    private lateinit var app: Context
    private var cliente: Client? = null
    private val ouvintes = CopyOnWriteArraySet<(TdApi.Object) -> Unit>()

    val configurado: Boolean get() = BuildConfig.TG_API_ID != 0 && BuildConfig.TG_API_HASH.isNotEmpty()

    fun iniciar(ctx: Context) {
        app = ctx.applicationContext
        if (!configurado) return
        try {
            Client.execute(TdApi.SetLogVerbosityLevel(1))
        } catch (_: Throwable) {
        }
        criarCliente()
    }

    private fun criarCliente() {
        cliente = Client.create({ aoAtualizar(it) }, null, null)
    }

    /** O ouvinte é chamado na thread principal. */
    fun ouvir(o: (TdApi.Object) -> Unit) = ouvintes.add(o)

    fun pararDeOuvir(o: (TdApi.Object) -> Unit) = ouvintes.remove(o)

    /** O retorno é entregue na thread principal. */
    fun enviar(q: TdApi.Function<*>, retorno: ((TdApi.Object) -> Unit)? = null) {
        cliente?.send(q) { r -> if (retorno != null) principal.post { retorno(r) } }
    }

    private fun aoAtualizar(o: TdApi.Object) {
        when (o) {
            is TdApi.UpdateAuthorizationState -> aoMudarAuth(o.authorizationState)
            is TdApi.UpdateNewChat -> chats[o.chat.id] = o.chat
            is TdApi.UpdateChatTitle -> chats[o.chatId]?.title = o.title
            is TdApi.UpdateChatLastMessage -> chats[o.chatId]?.positions = o.positions
            is TdApi.UpdateChatDraftMessage -> chats[o.chatId]?.positions = o.positions
            is TdApi.UpdateChatPosition -> chats[o.chatId]?.let { c ->
                val resto = c.positions.filterNot { mesmaLista(it.list, o.position.list) }
                c.positions = (if (o.position.order == 0L) resto else resto + o.position).toTypedArray()
            }
            is TdApi.UpdateChatFolders -> pastas = o.chatFolders
            is TdApi.UpdateFile -> {
                arquivos[o.file.id] = o.file
                synchronized(travaArquivos) { travaArquivos.notifyAll() }
            }
        }
        principal.post {
            when (o) {
                is TdApi.UpdateNewMessage -> AutoDownload.aoChegar(o.message)
                is TdApi.UpdateFile -> AutoDownload.aoAtualizarArquivo(o.file)
            }
            ouvintes.forEach { it(o) }
        }
    }

    private fun aoMudarAuth(estado: TdApi.AuthorizationState) {
        auth = estado
        when (estado) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val base = File(app.filesDir, "tdlib")
                enviar(
                    TdApi.SetTdlibParameters(
                        false,
                        File(base, "db").absolutePath,
                        File(base, "arquivos").absolutePath,
                        null,
                        true, true, true, false,
                        BuildConfig.TG_API_ID,
                        BuildConfig.TG_API_HASH,
                        Locale.getDefault().toLanguageTag(),
                        Build.MODEL,
                        Build.VERSION.RELEASE,
                        BuildConfig.VERSION_NAME,
                    )
                )
            }
            is TdApi.AuthorizationStateReady -> {
                aplicarLimites()
                limpar()
                carregarChats(TdApi.ChatListMain())
                principal.post { AutoDownload.sincronizar() }
            }
            is TdApi.AuthorizationStateClosed -> {
                chats.clear()
                arquivos.clear()
                pastas = emptyArray()
                criarCliente()
            }
        }
    }

    fun mesmaLista(a: TdApi.ChatList, b: TdApi.ChatList): Boolean {
        if (a.constructor != b.constructor) return false
        return a !is TdApi.ChatListFolder || a.chatFolderId == (b as TdApi.ChatListFolder).chatFolderId
    }

    fun ordem(c: TdApi.Chat, lista: TdApi.ChatList): Long =
        c.positions.firstOrNull { mesmaLista(it.list, lista) }?.order ?: 0

    /** Pede páginas à TDLib até ela responder que a lista acabou (erro 404). */
    fun carregarChats(lista: TdApi.ChatList) {
        enviar(TdApi.LoadChats(lista, 100)) { r -> if (r is TdApi.Ok) carregarChats(lista) }
    }

    fun arquivo(f: TdApi.File): TdApi.File = arquivos[f.id] ?: f

    fun baixar(idArquivo: Int, prioridade: Int, inicio: Long = 0) {
        enviar(TdApi.DownloadFile(idArquivo, prioridade, inicio, 0, false)) { r ->
            if (r is TdApi.File) arquivos[r.id] = r
        }
    }

    fun espacoLivre(): Long = app.filesDir.usableSpace

    fun aplicarLimites() {
        enviar(TdApi.SetOption("use_storage_optimizer", TdApi.OptionValueBoolean(true)))
        enviar(TdApi.SetOption("storage_max_files_size", TdApi.OptionValueInteger(Prefs.limiteBytes)))
        enviar(TdApi.SetOption("storage_max_time_from_last_access", TdApi.OptionValueInteger(idadeMaxima().toLong())))
    }

    private fun idadeMaxima(): Int = if (Prefs.dias == 0) Int.MAX_VALUE else Prefs.dias * 86400

    /**
     * Apaga os downloads mais antigos até caber no limite configurado.
     * [reservar] abre espaço adiantado para um vídeo que vai começar a baixar.
     */
    fun limpar(reservar: Long = 0, tudo: Boolean = false, retorno: ((TdApi.Object) -> Unit)? = null) {
        val tamanho = if (tudo) 0 else (Prefs.limiteBytes - reservar).coerceAtLeast(0)
        val idade = if (tudo) 0 else idadeMaxima()
        enviar(TdApi.OptimizeStorage(tamanho, idade, -1, 0, null, null, null, false, 0), retorno)
    }
}
