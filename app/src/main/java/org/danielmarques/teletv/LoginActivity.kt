package org.danielmarques.teletv

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.drinkless.tdlib.TdApi

class LoginActivity : AppCompatActivity() {
    private lateinit var msg: TextView
    private lateinit var qr: ImageView
    private lateinit var campo: EditText
    private lateinit var principal: Button
    private lateinit var secundario: Button
    private var estadoMostrado: Int = 0

    private val ouvinte: (TdApi.Object) -> Unit = { o ->
        if (o is TdApi.UpdateAuthorizationState) mostrar()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.login)
        msg = findViewById(R.id.msg)
        qr = findViewById(R.id.qr)
        campo = findViewById(R.id.campo)
        principal = findViewById(R.id.principal)
        secundario = findViewById(R.id.secundario)
        principal.setOnClickListener { confirmar() }
        campo.setOnEditorActionListener { _, acao, _ ->
            if (acao == EditorInfo.IME_ACTION_DONE) confirmar()
            false
        }
        secundario.setOnClickListener {
            if (Tg.auth is TdApi.AuthorizationStateWaitPhoneNumber) {
                Tg.enviar(TdApi.RequestQrCodeAuthentication(LongArray(0)), ::aoResponder)
            } else {
                // Volta ao início do login.
                Tg.enviar(TdApi.LogOut())
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Tg.loginAberto = true
        Tg.ouvir(ouvinte)
        mostrar()
    }

    override fun onStop() {
        super.onStop()
        Tg.loginAberto = false
        Tg.pararDeOuvir(ouvinte)
    }

    private fun mostrar() {
        val e = Tg.auth
        if (e is TdApi.AuthorizationStateReady) {
            finish()
            return
        }
        val mudou = e?.constructor != estadoMostrado
        estadoMostrado = e?.constructor ?: 0
        if (mudou) campo.setText("")
        qr.visibility = View.GONE
        campo.visibility = View.VISIBLE
        principal.visibility = View.VISIBLE
        secundario.visibility = View.VISIBLE
        secundario.text = "Voltar ao início"
        when (e) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                msg.text = "Digite seu número com o código do país (ex.: 5511999999999) ou entre apontando o celular para um QR code."
                campo.inputType = InputType.TYPE_CLASS_PHONE
                principal.text = "Enviar código"
                secundario.text = "Entrar com QR code"
            }
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                msg.text = "No celular: Telegram → Configurações → Dispositivos → Conectar dispositivo, e aponte a câmera para este código."
                qr.setImageBitmap(Qr.gerar(e.link))
                qr.visibility = View.VISIBLE
                campo.visibility = View.GONE
                principal.visibility = View.GONE
                if (mudou) secundario.requestFocus()
            }
            is TdApi.AuthorizationStateWaitCode -> {
                msg.text = "Digite o código que o Telegram enviou."
                campo.inputType = InputType.TYPE_CLASS_NUMBER
                principal.text = "Confirmar código"
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                val dica = if (e.passwordHint.isNullOrEmpty()) "" else " Dica: ${e.passwordHint}"
                msg.text = "Digite a senha da verificação em duas etapas.$dica"
                campo.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                principal.text = "Entrar"
            }
            else -> {
                msg.text = "Conectando ao Telegram…"
                campo.visibility = View.GONE
                principal.visibility = View.GONE
                secundario.visibility = View.GONE
            }
        }
        if (mudou && campo.visibility == View.VISIBLE) campo.requestFocus()
    }

    private fun confirmar() {
        val texto = campo.text.toString().trim()
        if (texto.isEmpty()) return
        when (Tg.auth) {
            is TdApi.AuthorizationStateWaitPhoneNumber ->
                Tg.enviar(TdApi.SetAuthenticationPhoneNumber(texto, null), ::aoResponder)
            is TdApi.AuthorizationStateWaitCode ->
                Tg.enviar(TdApi.CheckAuthenticationCode(texto), ::aoResponder)
            is TdApi.AuthorizationStateWaitPassword ->
                Tg.enviar(TdApi.CheckAuthenticationPassword(texto), ::aoResponder)
            else -> {}
        }
    }

    private fun aoResponder(r: TdApi.Object) {
        if (r is TdApi.Error) Toast.makeText(this, "Telegram: ${r.message}", Toast.LENGTH_LONG).show()
    }
}
