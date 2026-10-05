package org.danielmarques.teletv

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Números e datas no formato do idioma do aparelho. */
object Formato {
    fun tamanho(bytes: Long): String {
        val mb = bytes / 1048576.0
        val l = Locale.getDefault()
        return if (mb >= 1024) String.format(l, "%.1f GB", mb / 1024) else String.format(l, "%.0f MB", mb)
    }

    fun duracao(s: Int): String {
        val h = s / 3600
        val m = s % 3600 / 60
        val seg = s % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, seg) else String.format(Locale.ROOT, "%d:%02d", m, seg)
    }

    fun data(unix: Int): String = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(unix * 1000L))
}
