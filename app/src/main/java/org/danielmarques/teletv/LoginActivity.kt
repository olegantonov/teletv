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

/**
 * Login na TV e, ao lado, o QR code da [Ponte] para fazer o mesmo pelo celular.
 * Também é a tela de configuração da chave de API quando o app ainda não tem uma.
 */
class LoginActivity : AppCompatActivity() {
    private lateinit var msg: TextView
    private lateinit var qr: ImageView
    private lateinit var campo: EditText
    private lateinit var principal: Button
    private lateinit var secundario: Button
    private lateinit var ponte: View
    private var estadoMostrado = 0
    private val revisar = object : Runnable {
        override fun run() {
            // A página do celular muda o estado sem passar por um evento da TDLib (ex.: pedir a chave de API).
            mostrar()
            msg.postDelayed(this, 1500)
        }
    }

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
        ponte = findViewById(R.id.ponte)
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
        val endereco = Ponte.iniciar(this)
        if (endereco == null) {
            ponte.visibility = View.GONE
        } else {
            ponte.visibility = View.VISIBLE
            findViewById<ImageView>(R.id.ponte_qr).setImageBitmap(Qr.gerar(endereco, 440))
            findViewById<TextView>(R.id.ponte_texto).text = getString(R.string.bridge_caption) + "\n" + endereco
        }
        revisar.run()
    }

    override fun onStop() {
        super.onStop()
        msg.removeCallbacks(revisar)
        Ponte.parar()
        Ponte.pedirApi = false
        Tg.loginAberto = false
        Tg.pararDeOuvir(ouvinte)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Sem login não há o que mostrar atrás desta tela: Voltar sai do app.
        if (Tg.auth is TdApi.AuthorizationStateReady) finish() else finishAffinity()
    }

    private fun mostrar() {
        val e = Tg.auth
        val soCelular = !Tg.configurado || Ponte.pedirApi
        if (e is TdApi.AuthorizationStateReady && !soCelular) {
            finish()
            return
        }
        val codigo = if (soCelular) -1 else e?.constructor ?: 0
        val mudou = codigo != estadoMostrado
        estadoMostrado = codigo
        if (mudou) campo.setText("")
        qr.visibility = View.GONE
        campo.visibility = View.VISIBLE
        principal.visibility = View.VISIBLE
        secundario.visibility = View.VISIBLE
        secundario.setText(R.string.login_restart)
        when {
            soCelular -> {
                msg.setText(
                    when {
                        ponte.visibility != View.VISIBLE -> R.string.no_network
                        Tg.configurado -> R.string.setup_change_key
                        else -> R.string.setup_needed
                    }
                )
                esconderControles()
            }
            e is TdApi.AuthorizationStateWaitPhoneNumber -> {
                msg.setText(R.string.login_phone_msg)
                campo.inputType = InputType.TYPE_CLASS_PHONE
                principal.setText(R.string.login_send_code)
                secundario.setText(R.string.login_use_qr)
            }
            e is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                msg.setText(R.string.login_qr_msg)
                if (mudou) qr.setImageBitmap(Qr.gerar(e.link))
                qr.visibility = View.VISIBLE
                campo.visibility = View.GONE
                principal.visibility = View.GONE
                if (mudou) secundario.requestFocus()
            }
            e is TdApi.AuthorizationStateWaitCode -> {
                msg.setText(R.string.login_code_msg)
                campo.inputType = InputType.TYPE_CLASS_NUMBER
                principal.setText(R.string.login_confirm_code)
            }
            e is TdApi.AuthorizationStateWaitPassword -> {
                val pedido = getString(R.string.login_password_msg)
                msg.text = if (e.passwordHint.isNullOrEmpty()) pedido else getString(R.string.login_password_hint, pedido, e.passwordHint)
                campo.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                principal.setText(R.string.login_enter)
            }
            else -> {
                msg.setText(R.string.connecting)
                esconderControles()
            }
        }
        if (mudou && campo.visibility == View.VISIBLE) campo.requestFocus()
    }

    private fun esconderControles() {
        campo.visibility = View.GONE
        principal.visibility = View.GONE
        secundario.visibility = View.GONE
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
        if (r is TdApi.Error) Toast.makeText(this, getString(R.string.telegram_error, r.message), Toast.LENGTH_LONG).show()
    }
}
