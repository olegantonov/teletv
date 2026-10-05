package org.danielmarques.teletv

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.URL
import kotlin.concurrent.thread

/** Busca a versão mais recente nos releases do GitHub e instala o APK por cima. */
object Atualizador {
    private const val ULTIMO = "https://api.github.com/repos/${BuildConfig.REPO}/releases/latest"
    private const val UM_DIA = 24 * 3600 * 1000L

    /** [manual] vem do botão em Configurações: ignora o intervalo e sempre dá retorno. */
    fun verificar(a: AppCompatActivity, manual: Boolean) {
        if (!manual && System.currentTimeMillis() - Prefs.ultimaVerificacao < UM_DIA) return
        thread {
            try {
                val json = JSONObject(URL(ULTIMO).readText())
                val versao = json.getString("tag_name").removePrefix("v")
                val arquivos = json.getJSONArray("assets")
                val apk = (0 until arquivos.length())
                    .map { arquivos.getJSONObject(it).getString("browser_download_url") }
                    .firstOrNull { it.endsWith(".apk") }
                Prefs.ultimaVerificacao = System.currentTimeMillis()
                a.runOnUiThread {
                    if (a.isFinishing || a.isDestroyed) return@runOnUiThread
                    if (apk != null && maisNova(versao, BuildConfig.VERSION_NAME)) {
                        oferecer(a, versao, apk)
                    } else if (manual) {
                        avisar(a, "Você já está na versão mais recente (${BuildConfig.VERSION_NAME}).")
                    }
                }
            } catch (e: Exception) {
                if (manual) a.runOnUiThread { avisar(a, "Não foi possível verificar: ${e.message}") }
            }
        }
    }

    fun maisNova(remota: String, local: String): Boolean {
        val r = remota.split('.').map { it.toIntOrNull() ?: 0 }
        val l = local.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val d = r.getOrElse(i) { 0 } - l.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    private fun oferecer(a: AppCompatActivity, versao: String, url: String) {
        AlertDialog.Builder(a)
            .setTitle("TeleTV $versao disponível")
            .setMessage("Você está na ${BuildConfig.VERSION_NAME}. Baixar e instalar agora?")
            .setPositiveButton("Atualizar") { _, _ -> baixar(a, url) }
            .setNegativeButton("Depois", null)
            .show()
    }

    private fun baixar(a: AppCompatActivity, url: String) {
        avisar(a, "Baixando a atualização…")
        thread {
            try {
                val destino = File(a.cacheDir, "atualizacao/teletv.apk")
                destino.parentFile?.mkdirs()
                URL(url).openStream().use { entrada -> destino.outputStream().use { entrada.copyTo(it) } }
                a.runOnUiThread { instalar(a, destino) }
            } catch (e: Exception) {
                a.runOnUiThread { avisar(a, "Falha ao baixar: ${e.message}") }
            }
        }
    }

    private fun instalar(a: AppCompatActivity, apk: File) {
        if (Build.VERSION.SDK_INT >= 26 && !a.packageManager.canRequestPackageInstalls()) {
            avisar(a, "Autorize o TeleTV a instalar apps e toque em atualizar de novo.")
            try {
                a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
            } catch (_: Exception) {
                // Fire OS: Configurações → Minha Fire TV → Opções do desenvolvedor → Instalar apps desconhecidos.
            }
            return
        }
        val uri = FileProvider.getUriForFile(a, "${a.packageName}.arquivos", apk)
        a.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun avisar(a: AppCompatActivity, texto: String) = Toast.makeText(a, texto, Toast.LENGTH_LONG).show()
}
