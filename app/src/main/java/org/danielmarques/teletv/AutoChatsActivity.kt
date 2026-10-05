package org.danielmarques.teletv

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.drinkless.tdlib.TdApi

/** Lista de conversas com uma chave cada: ligada baixa os vídeos recentes sozinha, desligada não. */
class AutoChatsActivity : AppCompatActivity() {
    private var itens: List<TdApi.Chat> = emptyList()
    private var marcadas: Set<Long> = emptySet()
    private var filtro = ""
    private val busca = Busca(this)
    private val adaptador = Adaptador()
    private lateinit var buscar: Button

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(160, 40, 160, 0)
        }
        setContentView(raiz)
        raiz.addView(TextView(this).apply {
            setText(R.string.choose_chats)
            textSize = 26f
            setTextColor(getColor(R.color.destaque))
        })
        raiz.addView(TextView(this).apply {
            setText(R.string.choose_hint)
            textSize = 16f
            setTextColor(getColor(R.color.texto_fraco))
            setPadding(0, 8, 0, 16)
        })
        buscar = Button(this).apply {
            setText(R.string.search_chat)
            isAllCaps = false
            setTextColor(getColor(R.color.texto))
            setBackgroundResource(R.drawable.foco)
            setPadding(32, 0, 32, 0)
            setOnClickListener {
                busca.pedir(getString(R.string.search_chat), filtro) { texto ->
                    filtro = texto
                    text = if (texto.isEmpty()) getString(R.string.search_chat) else getString(R.string.search_active, texto)
                    recarregar()
                }
            }
        }
        raiz.addView(buscar, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = 12 })
        adaptador.setHasStableIds(true)
        raiz.addView(RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@AutoChatsActivity)
            adapter = adaptador
            clipToPadding = false
            setPadding(0, 0, 0, 40)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        // Os arquivados só chegam quando pedidos; sem isso ficariam fora da lista.
        Tg.carregarChats(TdApi.ChatListArchive())
        recarregar()
    }

    private fun recarregar() {
        marcadas = Prefs.autoChats
        val principal = TdApi.ChatListMain()
        // Marcadas primeiro; o resto na ordem da lista de conversas. A ordem só muda ao reabrir a busca ou a tela.
        itens = Tg.chats.values
            .filter { it.title.contains(filtro, ignoreCase = true) }
            .sortedWith(compareByDescending<TdApi.Chat> { it.id in marcadas }.thenByDescending { Tg.ordem(it, principal) })
        adaptador.notifyDataSetChanged()
    }

    private inner class Adaptador : RecyclerView.Adapter<Linha>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int): Linha {
            val nome = TextView(p.context).apply {
                textSize = 20f
                maxLines = 1
                setTextColor(getColor(R.color.texto))
            }
            val chave = SwitchCompat(p.context).apply {
                isFocusable = false
                isClickable = false
            }
            val linha = LinearLayout(p.context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.foco)
                setPadding(32, 18, 32, 18)
                isFocusable = true
                isClickable = true
                layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = 10 }
                addView(nome, LinearLayout.LayoutParams(0, -2, 1f))
                addView(chave)
            }
            return Linha(linha, nome, chave)
        }

        override fun getItemCount() = itens.size

        override fun getItemId(i: Int) = itens[i].id

        override fun onBindViewHolder(h: Linha, i: Int) {
            val c = itens[i]
            h.nome.text = c.title
            h.chave.isChecked = c.id in marcadas
            h.itemView.setOnClickListener {
                val ligar = c.id !in marcadas
                Prefs.definirAuto(c.id, ligar)
                marcadas = Prefs.autoChats
                h.chave.isChecked = ligar
                if (ligar) AutoDownload.sincronizar()
            }
        }
    }

    private class Linha(v: View, val nome: TextView, val chave: SwitchCompat) : RecyclerView.ViewHolder(v)
}
