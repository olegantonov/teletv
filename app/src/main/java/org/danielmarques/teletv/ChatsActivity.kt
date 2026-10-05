package org.danielmarques.teletv

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.drinkless.tdlib.TdApi

class ChatsActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var abas: LinearLayout
    private var lista: TdApi.ChatList = TdApi.ChatListMain()
    private var itens: List<TdApi.Chat> = emptyList()
    private var abasMontadas = ""
    private lateinit var rv: RecyclerView
    private val adaptador = Adaptador()
    private val busca = Busca(this)
    private var filtroNome = ""
    private lateinit var buscar: Button
    private val atualizar = Runnable { recarregar() }

    private val ouvinte: (TdApi.Object) -> Unit = { o ->
        when (o) {
            is TdApi.UpdateAuthorizationState -> aoMudarAuth()
            is TdApi.UpdateChatFolders -> montarAbas()
            is TdApi.UpdateNewChat, is TdApi.UpdateChatPosition, is TdApi.UpdateChatLastMessage,
            is TdApi.UpdateChatTitle -> agendar()
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.chats)
        status = findViewById(R.id.status)
        abas = findViewById(R.id.abas)
        adaptador.setHasStableIds(true)
        rv = findViewById(R.id.lista)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adaptador
        buscar = findViewById(R.id.buscar)
        buscar.setOnClickListener {
            busca.pedir("Buscar conversa", filtroNome) { texto ->
                filtroNome = texto
                buscar.text = if (texto.isEmpty()) "Buscar conversa" else "Busca: $texto"
                recarregar()
            }
        }
        Atualizador.verificar(this, manual = false)
        findViewById<Button>(R.id.config).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        Tg.ouvir(ouvinte)
        montarAbas()
        aoMudarAuth()
    }

    override fun onStop() {
        super.onStop()
        Tg.pararDeOuvir(ouvinte)
        Tg.principal.removeCallbacks(atualizar)
    }

    private fun aoMudarAuth() {
        if (!Tg.configurado) {
            status.text = "Falta configurar o api_id e o api_hash do Telegram neste APK."
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
            else -> status.text = "Conectando ao Telegram…"
        }
    }

    private fun montarAbas() {
        val opcoes = mutableListOf<Pair<String, TdApi.ChatList>>("Todos" to TdApi.ChatListMain())
        Tg.pastas.forEach { opcoes += it.name.text.text to TdApi.ChatListFolder(it.id) }
        opcoes += "Arquivados" to TdApi.ChatListArchive()
        // Reconstruir as abas tira o foco do controle; só refaz quando as pastas mudam.
        val assinatura = opcoes.joinToString("|") { it.first }
        if (assinatura == abasMontadas) return pintarAbas()
        abasMontadas = assinatura
        abas.removeAllViews()
        for ((nome, l) in opcoes) {
            val bt = Button(this).apply {
                text = nome
                tag = l
                isAllCaps = false
                setBackgroundResource(R.drawable.foco)
                setPadding(32, 0, 32, 0)
                setOnClickListener {
                    lista = l
                    Tg.carregarChats(l)
                    pintarAbas()
                    recarregar()
                }
            }
            abas.addView(bt, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = 12 })
        }
        pintarAbas()
    }

    private fun pintarAbas() {
        for (i in 0 until abas.childCount) {
            val bt = abas.getChildAt(i) as Button
            val ativa = Tg.mesmaLista(bt.tag as TdApi.ChatList, lista)
            bt.setTextColor(getColor(if (ativa) R.color.destaque else R.color.texto))
        }
    }

    private fun agendar() {
        Tg.principal.removeCallbacks(atualizar)
        Tg.principal.postDelayed(atualizar, 400)
    }

    private fun recarregar() {
        if (Tg.auth !is TdApi.AuthorizationStateReady) return
        itens = Tg.chats.values
            .filter { Tg.ordem(it, lista) != 0L && it.title.contains(filtroNome, ignoreCase = true) }
            .sortedByDescending { Tg.ordem(it, lista) }
        status.text = when {
            itens.isNotEmpty() -> "${itens.size} conversas"
            filtroNome.isNotEmpty() -> "Nenhuma conversa com \"$filtroNome\" nesta aba."
            else -> "Carregando conversas…"
        }
        adaptador.notifyDataSetChanged()
        if (itens.isNotEmpty() && currentFocus == null) rv.post { rv.getChildAt(0)?.requestFocus() }
    }

    private fun tipo(c: TdApi.Chat): String = when (val t = c.type) {
        is TdApi.ChatTypeSupergroup -> if (t.isChannel) "Canal" else "Grupo"
        is TdApi.ChatTypeBasicGroup -> "Grupo"
        else -> "Conversa"
    }

    private inner class Adaptador : RecyclerView.Adapter<Linha>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            Linha(LayoutInflater.from(p.context).inflate(R.layout.item_chat, p, false))

        override fun getItemCount() = itens.size

        override fun getItemId(i: Int) = itens[i].id

        override fun onBindViewHolder(h: Linha, i: Int) {
            val c = itens[i]
            h.nome.text = c.title
            h.tipo.text = tipo(c)
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
}
