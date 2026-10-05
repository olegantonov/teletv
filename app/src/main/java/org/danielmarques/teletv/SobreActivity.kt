package org.danielmarques.teletv

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SobreActivity : AppCompatActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val texto = TextView(this).apply {
            textSize = 17f
            setTextColor(getColor(R.color.texto))
            setLineSpacing(0f, 1.25f)
            setPadding(160, 48, 160, 48)
            text = SOBRE.replace("{versao}", BuildConfig.VERSION_NAME).replace("{repo}", BuildConfig.REPO)
        }
        // Focável para as setas do controle rolarem o texto.
        setContentView(ScrollView(this).apply {
            isFocusable = true
            addView(texto)
        })
    }

    private companion object {
        const val SOBRE = """TeleTV {versao}

Cliente não oficial do Telegram para TVs: navegue pelas suas conversas e assista aos vídeos pelo controle remoto.

Código-fonte e novas versões: github.com/{repo}

Este app não é afiliado ao Telegram nem endossado por ele. Ele usa a API pública do Telegram e se conecta apenas aos servidores do Telegram e, para procurar atualizações, ao GitHub. Não coleta nem envia dados de uso.


LICENÇA

MIT License. Copyright (c) 2026 Daniel Marques.

É concedida permissão, gratuitamente, a qualquer pessoa que obtenha uma cópia deste software, para usar, copiar, modificar, mesclar, publicar, distribuir, sublicenciar e vender cópias, desde que o aviso de copyright e esta permissão acompanhem todas as cópias. O software é fornecido "como está", sem garantia de qualquer tipo.


COMPONENTES DE TERCEIROS

TDLib (Telegram Database Library)
Copyright Aliaksei Levin e Arseny Smirnov. Boost Software License 1.0.
github.com/tdlib/td

AndroidX e Jetpack Media3 (ExoPlayer)
Copyright The Android Open Source Project. Apache License 2.0.
developer.android.com/jetpack

ZXing
Copyright ZXing authors. Apache License 2.0.
github.com/zxing/zxing

Kotlin
Copyright JetBrains s.r.o. Apache License 2.0.
kotlinlang.org

Os textos completos das licenças estão no repositório, no arquivo LICENCAS-TERCEIROS.md.
"""
    }
}
