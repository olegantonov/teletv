package org.danielmarques.teletv

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.drinkless.tdlib.TdApi
import java.util.concurrent.Executors

class VideosActivity : AppCompatActivity() {
    private class Item(
        val msg: Long,
        val arquivo: TdApi.File,
        val titulo: String,
        val duracao: Int,
        val capa: TdApi.Thumbnail?,
        val mini: ByteArray?,
        val data: Int,
    )

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

    private var chat = 0L
    private var soDocumentos = false
    private var paginas = 0
    private val todos = ArrayList<Item>()
    private var proximo = 0L
    private var carregando = false
    private var fim = false
    private var geracao = 0
    private var itens: List<Item> = emptyList()
    private val capas = LruCache<Int, Bitmap>(120)
    private val decodificador = Executors.newSingleThreadExecutor()
    private val adaptador = Adaptador()
    private val busca = Busca(this)
    private lateinit var status: TextView
    private lateinit var filtros: LinearLayout
    private lateinit var grade: RecyclerView

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
        findViewById<TextView>(R.id.titulo).text = intent.getStringExtra("titulo")
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
        reiniciar()
    }

    override fun onStart() {
        super.onStart()
        Tg.ouvir(ouvinte)
        adaptador.notifyDataSetChanged()
    }

    override fun onStop() {
        super.onStop()
        Tg.pararDeOuvir(ouvinte)
    }

    override fun onDestroy() {
        super.onDestroy()
        decodificador.shutdownNow()
    }

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
        botao({ getString(R.string.filter_date, opcoes(R.array.dates)[Filtro.periodo]) }, { Filtro.periodo != 0 }) {
            Filtro.periodo = (Filtro.periodo + 1) % Filtro.dias.size
            // Um período maior pode exigir mensagens que ainda não foram buscadas.
            fim = proximo == 0L && todos.isNotEmpty()
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
        botao({ if (Filtro.busca.isEmpty()) getString(R.string.search_name) else getString(R.string.search_active, Filtro.busca) }, { Filtro.busca.isNotEmpty() }) {
            pedirBusca()
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

    private fun opcoes(id: Int): Array<String> = resources.getStringArray(id)

    private fun pedirBusca() {
        busca.pedir(getString(R.string.search_in_chat), Filtro.busca) { texto ->
            Filtro.busca = texto
            montarFiltros()
            reiniciar()
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
        carregar()
    }

    private fun passa(it: Item): Boolean {
        val f = Tg.arquivo(it.arquivo)
        val mb = tamanho(f) / 1048576
        val dias = Filtro.dias[Filtro.periodo]
        if (dias != 0 && it.data < System.currentTimeMillis() / 1000 - dias * 86400L) return false
        val tamanhoOk = when (Filtro.tamanho) {
            1 -> mb <= 100
            2 -> mb in 100..500
            3 -> mb in 500..1024
            4 -> mb > 1024
            else -> true
        }
        val min = it.duracao / 60
        // Arquivos enviados como documento não informam duração: saem quando há filtro de duração.
        val duracaoOk = when (Filtro.duracao) {
            0 -> true
            1 -> it.duracao in 1..300
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
            2 -> filtrados.sortedByDescending { tamanho(Tg.arquivo(it.arquivo)) }
            3 -> filtrados.sortedBy { tamanho(Tg.arquivo(it.arquivo)) }
            4 -> filtrados.sortedByDescending { it.duracao }
            else -> filtrados
        }
        adaptador.notifyDataSetChanged()
        status.text = when {
            carregando -> getString(R.string.loading)
            todos.isEmpty() && fim -> getString(R.string.no_videos)
            else -> {
                val contagem = if (Filtro.ativo) getString(R.string.videos_filtered, itens.size, todos.size)
                else getString(R.string.videos_count, itens.size)
                if (fim) contagem else getString(R.string.has_more, contagem)
            }
        }
        if (buscarMais) {
            paginas = 0
            carregar()
        }
    }

    private fun carregar() {
        if (carregando || fim) return
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
            r.messages.mapNotNullTo(todos) { paraItem(it) }
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

    private fun paraItem(m: TdApi.Message): Item? = when (val c = m.content) {
        is TdApi.MessageVideo -> Item(
            m.id, c.video.video,
            c.caption.text.ifBlank { c.video.fileName }.ifBlank { getString(R.string.video_default) },
            c.video.duration, c.video.thumbnail, c.video.minithumbnail?.data, m.date,
        )
        is TdApi.MessageDocument ->
            if (ehVideo(c.document)) Item(
                m.id, c.document.document,
                c.document.fileName.ifBlank { c.caption.text }.ifBlank { getString(R.string.video_default) },
                0, c.document.thumbnail, c.document.minithumbnail?.data, m.date,
            ) else null
        else -> null
    }

    private fun ehVideo(d: TdApi.Document): Boolean =
        d.mimeType.startsWith("video/") ||
            d.fileName.substringAfterLast('.', "").lowercase() in setOf("mkv", "mp4", "avi", "mov", "webm", "ts", "m4v")

    private fun tamanho(f: TdApi.File) = if (f.size > 0) f.size else f.expectedSize

    private fun abrir(it: Item) {
        val f = Tg.arquivo(it.arquivo)
        val total = tamanho(f)
        val falta = total - f.local.downloadedSize
        if (f.local.isDownloadingCompleted) return tocar(it, total)
        status.setText(R.string.preparing)
        // Libera espaço antes: o que já está baixado mais este vídeo precisa caber no limite.
        Tg.limpar(reservar = falta) { _ ->
            val livre = filesDir.usableSpace
            if (falta > livre - 300L * 1048576) {
                Toast.makeText(
                    this,
                    getString(R.string.no_space, Formato.tamanho(total), Formato.tamanho(livre)),
                    Toast.LENGTH_LONG,
                ).show()
            } else {
                if (total > Prefs.limiteBytes) {
                    Toast.makeText(
                        this,
                        getString(R.string.over_limit, Formato.tamanho(Prefs.limiteBytes)),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                tocar(it, total)
            }
            aplicar()
        }
    }

    private fun tocar(it: Item, total: Long) {
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra("arquivo", it.arquivo.id)
                .putExtra("tamanho", total)
                .putExtra("chave", "${chat}_${it.msg}")
                .putExtra("titulo", it.titulo)
        )
    }

    private fun apagar(it: Item): Boolean {
        val f = Tg.arquivo(it.arquivo)
        if (f.local.downloadedSize == 0L) return false
        Tg.enviar(TdApi.DeleteFile(f.id)) { _ ->
            Toast.makeText(this, R.string.download_deleted, Toast.LENGTH_SHORT).show()
            adaptador.notifyDataSetChanged()
        }
        return true
    }

    private fun carregarCapa(it: Item, destino: ImageView) {
        val capa = it.capa
        destino.tag = it.msg
        capas.get(capa?.file?.id ?: -1)?.let { bmp -> return destino.setImageBitmap(bmp) }
        destino.setImageBitmap(it.mini?.let { d -> BitmapFactory.decodeByteArray(d, 0, d.size) })
        if (capa == null) return
        val f = Tg.arquivo(capa.file)
        if (!f.local.isDownloadingCompleted) return Tg.baixar(f.id, 1)
        decodificador.execute {
            val bmp = BitmapFactory.decodeFile(f.local.path) ?: return@execute
            capas.put(f.id, bmp)
            runOnUiThread { if (destino.tag == it.msg) destino.setImageBitmap(bmp) }
        }
    }

    private inner class Adaptador : RecyclerView.Adapter<Cartao>() {
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            Cartao(LayoutInflater.from(p.context).inflate(R.layout.item_video, p, false))

        override fun getItemCount() = itens.size

        override fun getItemId(i: Int) = itens[i].msg

        override fun onBindViewHolder(h: Cartao, i: Int) {
            val it = itens[i]
            val f = Tg.arquivo(it.arquivo)
            val total = tamanho(f)
            h.nome.text = it.titulo
            h.duracao.text = if (it.duracao > 0) Formato.duracao(it.duracao) else ""
            h.duracao.visibility = if (it.duracao > 0) View.VISIBLE else View.GONE
            val base = "${Formato.tamanho(total)} · ${Formato.data(it.data)}"
            h.meta.text = when {
                f.local.isDownloadingCompleted -> getString(R.string.meta_downloaded, base)
                f.local.downloadedSize > 0 && total > 0 ->
                    getString(R.string.meta_downloading, base, (f.local.downloadedSize * 100 / total).toInt())
                else -> base
            }
            carregarCapa(it, h.capa)
            h.itemView.setOnClickListener { _ -> abrir(it) }
            h.itemView.setOnLongClickListener { _ -> apagar(it) }
        }
    }

    private class Cartao(v: View) : RecyclerView.ViewHolder(v) {
        val capa: ImageView = v.findViewById(R.id.capa)
        val duracao: TextView = v.findViewById(R.id.duracao)
        val nome: TextView = v.findViewById(R.id.nome)
        val meta: TextView = v.findViewById(R.id.meta)
    }
}
