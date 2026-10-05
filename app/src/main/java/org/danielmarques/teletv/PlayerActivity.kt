package org.danielmarques.teletv

import android.os.Bundle
import android.widget.Toast
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
import androidx.media3.ui.PlayerView
import org.drinkless.tdlib.TdApi

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private var sessao: MediaSession? = null
    private var arquivo = 0
    private var chave = ""

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.player)
        arquivo = intent.getIntExtra("arquivo", 0)
        chave = intent.getStringExtra("chave") ?: ""
    }

    override fun onStart() {
        super.onStart()
        val tamanho = intent.getLongExtra("tamanho", 0)
        val fonte = ProgressiveMediaSource.Factory(DataSource.Factory { TdDataSource(arquivo, tamanho) })
            .createMediaSource(MediaItem.fromUri("td://arquivo/$arquivo"))
        val p = ExoPlayer.Builder(this).build()
        player = p
        // Com a sessão de mídia ativa, os comandos de voz do sistema (pausar, continuar, avançar) chegam ao player.
        sessao = MediaSession.Builder(this, p).build()
        findViewById<PlayerView>(R.id.player).player = p
        p.addListener(object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                Toast.makeText(this@PlayerActivity, "Não foi possível reproduzir: ${e.errorCodeName}", Toast.LENGTH_LONG).show()
            }

            override fun onPlaybackStateChanged(estado: Int) {
                if (estado == Player.STATE_ENDED) {
                    Prefs.salvarPosicao(chave, 0)
                    finish()
                }
            }
        })
        p.setMediaSource(fonte, Prefs.posicao(chave))
        p.prepare()
        p.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        player?.let { p ->
            val pos = p.currentPosition
            val dur = p.duration
            // Perto do fim conta como assistido: da próxima vez começa do zero.
            if (p.playbackState != Player.STATE_ENDED) {
                Prefs.salvarPosicao(chave, if (dur > 0 && pos > dur * 95 / 100) 0 else pos)
            }
            sessao?.release()
            p.release()
        }
        sessao = null
        player = null
        Tg.enviar(TdApi.CancelDownloadFile(arquivo, false)) { _ -> Tg.limpar() }
    }
}
