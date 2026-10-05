package org.danielmarques.teletv

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.drinkless.tdlib.TdApi

/** Tela inicial: "continuar assistindo" no topo, depois as conversas por aba. */
class ChatsActivity : AppCompatActivity() {
    private companion object {
        /** Marca a aba de conversas favoritas, que não é uma lista do Telegram. */
        const val ABA_FAVORITOS = "favoritos"
    }

    private lateinit var status: TextView
    private lateinit var abas: LinearLayout
    private var aba: Any = TdApi.ChatListMain()
    private var itens: List<TdApi.Chat> = emptyList()
    private var abasMontadas = ""
    private lateinit var rv: RecyclerView
    private val adaptador = Adaptador()
    private val busca = Busca(this)
    private var filtroNome = ""
    private var marcadas: Set<Long> = emptySet()
    private var favoritas: Set<Long> = emptySet()
    private lateinit var buscar: Button
    private val atualizar = Runnable { recarregar() }

    private lateinit var capas: Capas
    private lateinit var secaoContinuar: View
    private var emAndamento: List<Video> = emptyList()
    private val continuarAdaptador = ContinuarAdaptador()
    private var tentativasContinuar = 0
    private val tentarContinuar = Runnable { carregarContinuar() }

    private val ouvinte: (TdApi.Object) -> Unit = { o ->
        when (o) {
            is TdApi.UpdateAuthorizationState -> aoMudarAuth()
            is TdApi.UpdateChatFolders -> montarAbas()
            is TdApi.UpdateNewChat, is TdApi.UpdateChatPosition, is TdApi.UpdateChatLastMessage,
            is TdApi.UpdateChatTitle -> agendar()
            is TdApi.UpdateFile -> if (o.file.local.isDownloadingCompleted) {
                val i = emAndamento.indexOfFirst { it.capa?.file?.id == o.file.id }
                if (i >= 0) continuarAdaptador.notifyItemChanged(i)
            }
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.chats)
        capas = Capas(this)
        status = findViewById(R.id.status)
        abas = findViewById(R.id.abas)
        adaptador.setHasStableIds(true)
        rv = findViewById(R.id.lista)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adaptador
        secaoContinuar = findViewById(R.id.secao_continuar)
        continuarAdaptador.setHasStableIds(true)
        findViewById<RecyclerView>(R.id.continuar).apply {
            layoutManager = LinearLayoutManager(this@ChatsActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = continuarAdaptador
        }
        buscar = findViewById(R.id.buscar)
        buscar.setOnClickListener {
            busca.pedir(getString(R.string.search_chat), filtroNome) { texto ->
                filtroNome = texto
                buscar.text = if (texto.isEmpty()) getString(R.string.search_chat) else getString(R.string.search_active, texto)
                recarregar()
            }
        }
        if (Prefs.autoAtualizar) Atualizador.verificar(this, manual = false)
        findViewById<Button>(R.id.biblioteca).setOnClickListener {
            // Abre no histórico: o "continuar assistindo" já está nesta tela.
            startActivity(Intent(this, VideosActivity::class.java).putExtra("secao", 1))
        }
        findViewById<Button>(R.id.config).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        // Com o app travado, a tela de senha vem primeiro; ao fechar, este onStart roda de novo.
        if (Prefs.temSenha && !Trava.liberado) return
        if (!Prefs.guiaFeito) {
            // Primeira execução: o guia cuida do login e das escolhas iniciais antes da lista.
            startActivity(Intent(this, GuiaActivity::class.java))
            return
        }
        Tg.ouvir(ouvinte)
        marcadas = Prefs.autoChats
        favoritas = Biblioteca.chatsFavoritos
        montarAbas()
        aoMudarAuth()
        tentativasContinuar = 0
        carregarContinuar()
    }

    override fun onStop() {
        super.onStop()
        Tg.pararDeOuvir(ouvinte)
        Tg.principal.removeCallbacks(atualizar)
        Tg.principal.removeCallbacks(tentarContinuar)
    }

    override fun onDestroy() {
        super.onDestroy()
        capas.encerrar()
    }

    private fun aoMudarAuth() {
        if (!Tg.configurado) {
            status.text = getString(R.string.missing_api)
            if (!Tg.loginAberto) startActivity(Intent(this, LoginActivity::class.java))
            return
        }
        when (Tg.auth) {
            is TdApi.AuthorizationStateReady -> recarregar()
            is TdApi.AuthorizationStateWaitPhoneNumber,
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation,
            is TdApi.AuthorizationStateWaitCode,
            is TdApi.AuthorizationStateWaitPassword -> {
                itens = emptyList()
                adaptador.notifyDataSetChanged()
                if (!Tg.loginAberto) startActivity(Intent(this, LoginActivity::class.java))
            }
            else -> status.text = getString(R.string.connecting)
        }
    }

    private fun carregarContinuar() {
        val entradas = Biblioteca.continuar.take(12)
        if (entradas.isEmpty() || Tg.auth !is TdApi.AuthorizationStateReady) {
            mostrarContinuar(emptyList())
            // Logo após abrir o app o login ainda está sendo retomado; tenta de novo em seguida.
            if (entradas.isNotEmpty()) repetirContinuar()
            return
        }
        Midia.resolver(this, entradas) { videos ->
            mostrarContinuar(videos)
            // As mensagens só são encontradas depois que a conversa delas foi carregada.
            if (videos.size < entradas.size) repetirContinuar()
        }
    }

    private fun repetirContinuar() {
        if (++tentativasContinuar > 4) return
        Tg.principal.removeCallbacks(tentarContinuar)
        Tg.principal.postDelayed(tentarContinuar, 2500)
    }

    private fun mostrarContinuar(videos: List<Video>) {
        emAndamento = videos
        continuarAdaptador.notifyDataSetChanged()
        secaoContinuar.visibility = if (videos.isEmpty()) View.GONE else View.VISIBLE
        pintarAbas()
    }

    private fun mesmaAba(a: Any, b: Any): Boolean =
        if (a is TdApi.ChatList && b is TdApi.ChatList) Tg.mesmaLista(a, b) else a == b

    private fun montarAbas() {
        val opcoes = mutableListOf<Pair<String, Any>>(
            getString(R.string.tab_all) to TdApi.ChatListMain(),
            getString(R.string.tab_favorites) to ABA_FAVORITOS,
        )
        Tg.pastas.forEach { opcoes += it.name.text.text to TdApi.ChatListFolder(it.id) }
        opcoes += getString(R.string.tab_archived) to TdApi.ChatListArchive()
        // Reconstruir as abas tira o foco do controle; só refaz quando as pastas mudam.
        val assinatura = opcoes.joinToString("|") { it.first }
        if (assinatura == abasMontadas) return pintarAbas()
        abasMontadas = assinatura
        abas.removeAllViews()
        for ((nome, destino) in opcoes) {
            val bt = Button(this).apply {
                text = nome
                tag = destino
                isAllCaps = false
                setBackgroundResource(R.drawable.foco)
                setPadding(32, 0, 32, 0)
                setOnClickListener {
                    aba = destino
                    if (destino is TdApi.ChatList) Tg.carregarChats(destino)
                    pintarAbas()
                    recarregar()
                }
            }
            abas.addView(bt, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = 12 })
        }
        pintarAbas()
    }

    private fun pintarAbas() {
        // Sem a fileira "continuar assistindo", acima das abas só há os botões do topo, alinhados à direita.
        val acima = if (secaoContinuar.visibility == View.VISIBLE) View.NO_ID else R.id.buscar
        for (i in 0 until abas.childCount) {
            val bt = abas.getChildAt(i) as Button
            bt.setTextColor(getColor(if (mesmaAba(bt.tag, aba)) R.color.destaque else R.color.texto))
            bt.nextFocusUpId = acima
        }
    }

    private fun agendar() {
        Tg.principal.removeCallbacks(atualizar)
        Tg.principal.postDelayed(atualizar, 400)
    }

    private fun recarregar() {
        if (Tg.auth !is TdApi.AuthorizationStateReady) return
        val principal = TdApi.ChatListMain()
        val lista = aba as? TdApi.ChatList
        itens = Tg.chats.values
            .filter { c ->
                val naAba = if (lista != null) Tg.ordem(c, lista) != 0L else c.id in favoritas
                naAba && c.title.contains(filtroNome, ignoreCase = true)
            }
            .sortedByDescending { Tg.ordem(it, lista ?: principal) }
        status.text = when {
            itens.isNotEmpty() -> getString(R.string.hold_hint, resources.getQuantityString(R.plurals.chats_count, itens.size, itens.size))
            filtroNome.isNotEmpty() -> getString(R.string.no_chat_match, filtroNome)
            lista == null -> getString(R.string.no_fav_chats)
            else -> getString(R.string.loading_chats)
        }
        adaptador.notifyDataSetChanged()
        if (itens.isNotEmpty() && currentFocus == null) rv.post { rv.getChildAt(0)?.requestFocus() }
    }

    private fun tipo(c: TdApi.Chat): String = when (val t = c.type) {
        is TdApi.ChatTypeSupergroup -> getString(if (t.isChannel) R.string.type_channel else R.string.type_group)
        is TdApi.ChatTypeBasicGroup -> getString(R.string.type_group)
        else -> getString(R.string.type_chat)
    }

    /** Menu de uma conversa, aberto ao segurar OK. */
    private fun menu(c: TdApi.Chat) {
        val favorita = c.id in favoritas
        val automatica = c.id in marcadas
        val opcoes = arrayOf(
            getString(if (favorita) R.string.fav_remove else R.string.fav_add),
            getString(if (automatica) R.string.auto_turn_off else R.string.auto_turn_on),
        )
        AlertDialog.Builder(this)
            .setTitle(c.title)
            .setItems(opcoes) { _, i ->
                if (i == 0) {
                    Biblioteca.alternarChat(c.id)
                    favoritas = Biblioteca.chatsFavoritos
                } else {
                    Prefs.definirAuto(c.id, !automatica)
                    marcadas = Prefs.autoChats
                    if (!automatica) AutoDownload.sincronizar()
                }
                recarregar()
            }
            .show()
    }

    private inner class Adaptador : RecyclerView.Adapter<Linha>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            Linha(LayoutInflater.from(p.context).inflate(R.layout.item_chat, p, false))

        override fun getItemCount() = itens.size

        override fun getItemId(i: Int) = itens[i].id

        override fun onBindViewHolder(h: Linha, i: Int) {
            val c = itens[i]
            h.nome.text = if (c.id in favoritas) getString(R.string.chat_fav, c.title) else c.title
            h.tipo.text = if (c.id in marcadas) getString(R.string.chat_auto, tipo(c)) else tipo(c)
            h.tipo.setTextColor(getColor(if (c.id in marcadas) R.color.destaque else R.color.texto_fraco))
            h.itemView.setOnLongClickListener {
                menu(c)
                true
            }
            h.itemView.setOnClickListener {
                startActivity(
                    Intent(this@ChatsActivity, VideosActivity::class.java)
                        .putExtra("chat", c.id)
                        .putExtra("titulo", c.title)
                )
            }
        }
    }

    private class Linha(v: View) : RecyclerView.ViewHolder(v) {
        val nome: TextView = v.findViewById(R.id.nome)
        val tipo: TextView = v.findViewById(R.id.tipo)
    }

    private inner class ContinuarAdaptador : RecyclerView.Adapter<CartaoVideo>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int): CartaoVideo {
            val v = LayoutInflater.from(p.context).inflate(R.layout.item_video, p, false)
            // Na fileira horizontal o cartão precisa de largura própria; na grade ela vem da coluna.
            v.layoutParams = RecyclerView.LayoutParams((300 * resources.displayMetrics.density).toInt(), -2).apply {
                marginEnd = 12
                topMargin = 8
            }
            return CartaoVideo(v)
        }

        override fun getItemCount() = emAndamento.size

        override fun getItemId(i: Int) = emAndamento[i].chave.hashCode().toLong()

        override fun onBindViewHolder(h: CartaoVideo, i: Int) {
            val v = emAndamento[i]
            h.mostrar(v, capas)
            h.itemView.setOnClickListener { Midia.abrir(this@ChatsActivity, v) }
            h.itemView.setOnLongClickListener {
                MenuVideo.abrir(this@ChatsActivity, v) { carregarContinuar() }
                true
            }
        }
    }
}
