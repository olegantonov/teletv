package org.danielmarques.teletv

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Caixa de busca. No campo de texto vale o ditado do teclado da própria TV;
 * onde o aparelho tem reconhecimento de voz para apps (Android TV), aparece o botão "Falar".
 * Precisa ser criada como campo da Activity, antes de ela iniciar.
 */
class Busca(private val a: AppCompatActivity) {
    private var retorno: ((String) -> Unit)? = null

    private val voz = a.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val texto = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == Activity.RESULT_OK && texto != null) retorno?.invoke(texto)
    }

    fun pedir(titulo: String, atual: String, aoResponder: (String) -> Unit) {
        retorno = aoResponder
        val campo = EditText(a).apply {
            setText(atual)
            setSingleLine()
            hint = "Digite ou dite pelo microfone do controle"
        }
        val falar = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
        val caixa = AlertDialog.Builder(a)
            .setTitle(titulo)
            .setView(campo)
            .setPositiveButton("Buscar") { _, _ -> aoResponder(campo.text.toString().trim()) }
            .setNeutralButton("Limpar") { _, _ -> aoResponder("") }
        if (falar.resolveActivity(a.packageManager) != null) {
            caixa.setNegativeButton("Falar") { _, _ -> voz.launch(falar) }
        }
        caixa.show()
    }
}
