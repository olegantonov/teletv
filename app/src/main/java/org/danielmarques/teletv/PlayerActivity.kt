package org.danielmarques.teletv

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import org.drinkless.tdlib.TdApi

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {
    private companion object {
        const val PULO_MS = 10_000L
        const val JANELA_SAIDA_MS = 2_500L
    }

    private var player: ExoPlayer? = null
    private var sessao: MediaSession? = null
    private var arquivo = 0
    private var chave = ""
    private var chat = 0L
    private var msg = 0L
    private var nome = ""
    private var ultimoVoltar = 0L
    private var zoom = false
    private lateinit var tela: PlayerView
    private lateinit var aviso: TextView
    private val esconderAviso = Runnable { aviso.visibility = View.GONE }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.player)
        arquivo = intent.getIntExtra("arquivo", 0)
        chat = intent.getLongExtra("chat", 0)
        msg = intent.getLongExtra("msg", 0)
        chave = "${chat}_$msg"
        nome = intent.getStringExtra("titulo") ?: ""
        tela = findViewById(R.id.player)
        aviso = findViewById(R.id.aviso)
        val titulo = findViewById<TextView>(R.id.titulo)
        titulo.text = intent.getStringExtra("titulo")
        tela.controllerShowTimeoutMs = 4000
        tela.controllerAutoShow = false
        tela.setShowSubtitleButton(true)
        tela.setShowNextButton(false)
        tela.setShowPreviousButton(false)
        tela.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { titulo.visibility = it })
    }

    override fun onStart() {
        super.onStart()
        val tamanho = intent.getLongExtra("tamanho", 0)
        val fonte = ProgressiveMediaSource.Factory(DataSource.Factory { TdDataSource(arquivo, tamanho) })
            .createMediaSource(MediaItem.fromUri("td://arquivo/$arquivo"))
        val p = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(PULO_MS)
            .setSeekForwardIncrementMs(PULO_MS)
            .build()
        player = p
        // Com a sessão de mídia ativa, os comandos de voz do sistema (pausar, continuar, avançar) chegam ao player.
        sessao = MediaSession.Builder(this, p).build()
        tela.player = p
        p.addListener(object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                avisar(getString(R.string.play_error, e.errorCodeName))
            }

            override fun onPlaybackStateChanged(estado: Int) {
                if (estado == Player.STATE_ENDED) {
                    Prefs.salvarPosicao(chave, 0)
                    Biblioteca.registrar(chat, msg, nome, 0, player?.duration ?: 0)
                    finish()
                }
            }
        })
        p.setMediaSource(fonte, Prefs.posicao(chave))
        p.prepare()
        p.playWhenReady = true
        tela.hideController()
    }

    override fun onStop() {
        super.onStop()
        aviso.removeCallbacks(esconderAviso)
        player?.let { p ->
            val pos = p.currentPosition
            val dur = p.duration
            // Perto do fim conta como assistido: da próxima vez começa do zero.
            if (p.playbackState != Player.STATE_ENDED) {
                val retomar = if (dur > 0 && pos > dur * 95 / 100) 0 else pos
                Prefs.salvarPosicao(chave, retomar)
                Biblioteca.registrar(chat, msg, nome, retomar, dur.coerceAtLeast(0))
            }
            sessao?.release()
            p.release()
        }
        sessao = null
        player = null
        Tg.enviar(TdApi.CancelDownloadFile(arquivo, false)) { _ -> Tg.limpar() }
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val sentido = when (e.keyCode) {
            KeyEvent.KEYCODE_MEDIA_REWIND -> -1
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> 1
            // Com os controles escondidos, esquerda e direita pulam direto, sem abrir a barra.
            KeyEvent.KEYCODE_DPAD_LEFT -> if (tela.isControllerFullyVisible) 0 else -1
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (tela.isControllerFullyVisible) 0 else 1
            else -> 0
        }
        if (sentido != 0) {
            // O soltar da tecla também é consumido: se chegasse ao PlayerView, ele abriria os controles.
            if (e.action == KeyEvent.ACTION_DOWN) pular(sentido, e)
            return true
        }
        if (e.keyCode == KeyEvent.KEYCODE_MENU) {
            if (e.action == KeyEvent.ACTION_DOWN) alternarZoom()
            return true
        }
        return super.dispatchKeyEvent(e)
    }

    /** Segurar a tecla acelera: 10 s, depois 30 s, depois 60 s por repetição. */
    private fun pular(sentido: Int, e: KeyEvent) {
        val p = player ?: return
        val fator = when {
            e.repeatCount > 30 -> 6
            e.repeatCount > 10 -> 3
            else -> 1
        }
        val dur = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val destino = (p.currentPosition + sentido * fator * PULO_MS).coerceIn(0, dur)
        p.seekTo(destino)
        val total = if (p.duration > 0) " / ${Formato.duracao((p.duration / 1000).toInt())}" else ""
        avisar((if (sentido > 0) "»  " else "«  ") + Formato.duracao((destino / 1000).toInt()) + total)
    }

    private fun alternarZoom() {
        zoom = !zoom
        tela.resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
        avisar(getString(if (zoom) R.string.zoom_fill else R.string.zoom_fit))
    }

    private fun avisar(texto: String) {
        aviso.text = texto
        aviso.visibility = View.VISIBLE
        aviso.removeCallbacks(esconderAviso)
        aviso.postDelayed(esconderAviso, 1800)
    }

    /** Um toque em Voltar esconde os controles; dois toques seguidos saem do vídeo. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val agora = SystemClock.elapsedRealtime()
        if (tela.isControllerFullyVisible) {
            tela.hideController()
            ultimoVoltar = 0
        } else if (agora - ultimoVoltar < JANELA_SAIDA_MS) {
            finish()
        } else {
            ultimoVoltar = agora
            avisar(getString(R.string.back_again))
        }
    }
}
