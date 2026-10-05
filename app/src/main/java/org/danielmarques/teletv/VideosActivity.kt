package org.danielmarques.teletv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.drinkless.tdlib.TdApi

/**
 * Grade de vídeos. Sem o extra "chat" vira a Biblioteca: em vez de buscar numa conversa,
 * mostra o que está no histórico ou nos favoritos.
 */
class VideosActivity : AppCompatActivity() {
    /** Filtros valem para todas as conversas enquanto o app estiver aberto. */
    private object Filtro {
        val dias = intArrayOf(0, 7, 30, 90, 365)
        var periodo = 0
        var tamanho = 0
        var duracao = 0
        var ordem = 0
        var soBaixados = false
        var busca = ""

        val ativo get() = periodo != 0 || tamanho != 0 || duracao != 0 || soBaixados
    }

    private companion object {
        const val CONTINUAR = 0
        const val HISTORICO = 1
        const val FAVORITOS = 2
    }

    private var chat = 0L
    private var secao = CONTINUAR
    private var soDocumentos = false
    private var paginas = 0
    private val todos = ArrayList<Video>()
    private var proximo = 0L
    private var carregando = false
    private var fim = false
    private var geracao = 0
    private var itens: List<Video> = emptyList()
    private val adaptador = Adaptador()
    private val busca = Busca(this)
    private lateinit var capas: Capas
    private lateinit var status: TextView
    private lateinit var filtros: LinearLayout
    private lateinit var grade: RecyclerView

    private val biblioteca get() = chat == 0L

    private val ouvinte: (TdApi.Object) -> Unit = { o ->
        if (o is TdApi.UpdateFile && o.file.local.isDownloadingCompleted) {
            val i = itens.indexOfFirst { it.capa?.file?.id == o.file.id || it.arquivo.id == o.file.id }
            if (i >= 0) adaptador.notifyItemChanged(i)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.videos)
        chat = intent.getLongExtra("chat", 0)
        secao = intent.getIntExtra("secao", CONTINUAR)
        capas = Capas(this)
        findViewById<TextView>(R.id.titulo).text =
            if (biblioteca) getString(R.string.library) else intent.getStringExtra("titulo")
        status = findViewById(R.id.status)
        filtros = findViewById(R.id.filtros)
        grade = findViewById(R.id.grade)
        grade.layoutManager = GridLayoutManager(this, 4)
        adaptador.setHasStableIds(true)
        grade.adapter = adaptador
        grade.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val ultimo = (rv.layoutManager as GridLayoutManager).findLastVisibleItemPosition()
                if (dy > 0 && ultimo >= itens.size - 12) {
                    paginas = 0
                    carregar()
                }
            }
        })
        montarFiltros()
        if (!biblioteca) reiniciar()
    }

    override fun onStart() {
        super.onStart()
        Tg.ouvir(ouvinte)
        // A biblioteca muda a cada vídeo assistido: relê ao voltar do player.
        if (biblioteca) reiniciar() else adaptador.notifyDataSetChanged()
    }

    override fun onStop() {
        super.onStop()
        Tg.pararDeOuvir(ouvinte)
    }

    override fun onDestroy() {
        super.onDestroy()
        capas.encerrar()
    }

    private fun opcoes(id: Int): Array<String> = resources.getStringArray(id)

    private fun montarFiltros() {
        filtros.removeAllViews()
        fun botao(texto: () -> String, ativo: () -> Boolean, aoClicar: () -> Unit) {
            val bt = Button(this)
            fun pintar() {
                bt.text = texto()
                bt.setTextColor(getColor(if (ativo()) R.color.destaque else R.color.texto))
            }
            bt.isAllCaps = false
            bt.setBackgroundResource(R.drawable.foco)
            bt.setPadding(28, 0, 28, 0)
            bt.setOnClickListener {
                aoClicar()
                pintar()
            }
            pintar()
            filtros.addView(bt, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = 10 })
        }
        if (biblioteca) {
            val nomes = intArrayOf(R.string.sec_continue, R.string.sec_history, R.string.sec_favs)
            botao({ getString(nomes[secao]) }, { true }) {
                secao = (secao + 1) % nomes.size
                reiniciar()
            }
        }
        botao({ getString(R.string.filter_date, opcoes(R.array.dates)[Filtro.periodo]) }, { Filtro.periodo != 0 }) {
            Filtro.periodo = (Filtro.periodo + 1) % Filtro.dias.size
            // Um período maior pode exigir mensagens que ainda não foram buscadas.
            if (!biblioteca) fim = proximo == 0L && todos.isNotEmpty()
            aplicar(buscarMais = true)
        }
        botao({ getString(R.string.filter_size, opcoes(R.array.sizes)[Filtro.tamanho]) }, { Filtro.tamanho != 0 }) {
            Filtro.tamanho = (Filtro.tamanho + 1) % opcoes(R.array.sizes).size
            aplicar(buscarMais = true)
        }
        botao({ getString(R.string.filter_duration, opcoes(R.array.durations)[Filtro.duracao]) }, { Filtro.duracao != 0 }) {
            Filtro.duracao = (Filtro.duracao + 1) % opcoes(R.array.durations).size
            aplicar(buscarMais = true)
        }
        botao({ getString(R.string.filter_order, opcoes(R.array.orders)[Filtro.ordem]) }, { Filtro.ordem != 0 }) {
            Filtro.ordem = (Filtro.ordem + 1) % opcoes(R.array.orders).size
            aplicar()
        }
        botao({ getString(if (Filtro.soBaixados) R.string.only_downloaded else R.string.all_downloads) }, { Filtro.soBaixados }) {
            Filtro.soBaixados = !Filtro.soBaixados
            aplicar(buscarMais = true)
        }
        // O que segue só faz sentido dentro de uma conversa.
        if (biblioteca) return
        botao(
            { if (Filtro.busca.isEmpty()) getString(R.string.search_name) else getString(R.string.search_active, Filtro.busca) },
            { Filtro.busca.isNotEmpty() },
        ) {
            busca.pedir(getString(R.string.search_in_chat), Filtro.busca) { texto ->
                Filtro.busca = texto
                montarFiltros()
                reiniciar()
            }
        }
        botao({ getString(if (soDocumentos) R.string.kind_files else R.string.kind_videos) }, { soDocumentos }) {
            soDocumentos = !soDocumentos
            reiniciar()
        }
        botao({ getString(if (chat in Prefs.autoChats) R.string.auto_on else R.string.auto_off) }, { chat in Prefs.autoChats }) {
            val ligar = chat !in Prefs.autoChats
            Prefs.definirAuto(chat, ligar)
            if (ligar) AutoDownload.sincronizar()
        }
    }

    private fun reiniciar() {
        geracao++
        todos.clear()
        proximo = 0
        fim = false
        carregando = false
        paginas = 0
        aplicar()
        if (biblioteca) carregarBiblioteca() else carregar()
    }

    private fun carregarBiblioteca() {
        carregando = true
        status.setText(R.string.loading)
        val entradas = when (secao) {
            CONTINUAR -> Biblioteca.continuar
            HISTORICO -> Biblioteca.historico
            else -> Biblioteca.videosFavoritos
        }
        val estaGeracao = geracao
        Midia.resolver(this, entradas) { videos ->
            if (estaGeracao != geracao) return@resolver
            carregando = false
            fim = true
            todos.addAll(videos)
            aplicar()
            if (itens.isNotEmpty()) grade.post { grade.getChildAt(0)?.requestFocus() }
        }
    }

    private fun passa(v: Video): Boolean {
        val f = Tg.arquivo(v.arquivo)
        val mb = Midia.tamanho(f) / 1048576
        val dias = Filtro.dias[Filtro.periodo]
        if (dias != 0 && v.data < System.currentTimeMillis() / 1000 - dias * 86400L) return false
        val tamanhoOk = when (Filtro.tamanho) {
            1 -> mb <= 100
            2 -> mb in 100..500
            3 -> mb in 500..1024
            4 -> mb > 1024
            else -> true
        }
        val min = v.duracao / 60
        // Arquivos enviados como documento não informam duração: saem quando há filtro de duração.
        val duracaoOk = when (Filtro.duracao) {
            0 -> true
            1 -> v.duracao in 1..300
            2 -> min in 5..20
            3 -> min in 20..60
            else -> min > 60
        }
        return tamanhoOk && duracaoOk && (!Filtro.soBaixados || f.local.isDownloadingCompleted)
    }

    private fun aplicar(buscarMais: Boolean = false) {
        val filtrados = todos.filter(::passa)
        itens = when (Filtro.ordem) {
            1 -> filtrados.sortedBy { it.data }
            2 -> filtrados.sortedByDescending { Midia.tamanho(Tg.arquivo(it.arquivo)) }
            3 -> filtrados.sortedBy { Midia.tamanho(Tg.arquivo(it.arquivo)) }
            4 -> filtrados.sortedByDescending { it.duracao }
            else -> filtrados
        }
        adaptador.notifyDataSetChanged()
        status.text = when {
            carregando -> getString(R.string.loading)
            todos.isEmpty() && fim -> getString(if (biblioteca) R.string.lib_empty else R.string.no_videos)
            else -> {
                val contagem = if (Filtro.ativo) getString(R.string.videos_filtered, itens.size, todos.size)
                else resources.getQuantityString(R.plurals.videos_count, itens.size, itens.size)
                if (fim) contagem else getString(R.string.has_more, contagem)
            }
        }
        if (buscarMais && !biblioteca) {
            paginas = 0
            carregar()
        }
    }

    private fun carregar() {
        if (biblioteca || carregando || fim) return
        // Com filtro ativo, busca sozinho até encher a tela, mas não a conversa inteira de uma vez.
        if (itens.size >= 12 && paginas > 0) return
        if (paginas >= 10) return
        carregando = true
        paginas++
        status.setText(R.string.loading)
        val filtro = if (soDocumentos) TdApi.SearchMessagesFilterDocument() else TdApi.SearchMessagesFilterVideo()
        val estaGeracao = geracao
        Tg.enviar(TdApi.SearchChatMessages(chat, null, Filtro.busca, null, proximo, 0, 50, filtro)) { r ->
            if (estaGeracao != geracao) return@enviar
            carregando = false
            if (r !is TdApi.FoundChatMessages) {
                fim = true
                status.text = getString(R.string.load_error, (r as? TdApi.Error)?.message)
                return@enviar
            }
            val primeiraCarga = todos.isEmpty()
            r.messages.mapNotNullTo(todos) { Midia.deMensagem(this, it) }
            proximo = r.nextFromMessageId
            fim = proximo == 0L || r.messages.isEmpty()
            // As mensagens vêm da mais nova para a mais antiga: passou do período, não há por que continuar.
            val dias = Filtro.dias[Filtro.periodo]
            val maisAntiga = r.messages.lastOrNull()?.date ?: 0
            if (dias != 0 && maisAntiga < System.currentTimeMillis() / 1000 - dias * 86400L) fim = true
            aplicar()
            if (primeiraCarga && itens.isNotEmpty()) grade.post { grade.getChildAt(0)?.requestFocus() }
            carregar()
        }
    }

    private inner class Adaptador : RecyclerView.Adapter<CartaoVideo>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            CartaoVideo(LayoutInflater.from(p.context).inflate(R.layout.item_video, p, false))

        override fun getItemCount() = itens.size

        // Na biblioteca há vídeos de várias conversas; o id da mensagem sozinho poderia repetir.
        override fun getItemId(i: Int) = itens[i].chave.hashCode().toLong()

        override fun onBindViewHolder(h: CartaoVideo, i: Int) {
            val v = itens[i]
            h.mostrar(v, capas)
            h.itemView.setOnClickListener {
                Midia.abrir(this@VideosActivity, v) { preparando ->
                    if (preparando) status.setText(R.string.preparing) else aplicar()
                }
            }
            h.itemView.setOnLongClickListener {
                MenuVideo.abrir(this@VideosActivity, v) { if (biblioteca) reiniciar() else notifyDataSetChanged() }
                true
            }
        }
    }
}
