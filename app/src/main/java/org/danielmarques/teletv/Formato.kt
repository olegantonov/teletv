package org.danielmarques.teletv

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formato {
    private val br = Locale("pt", "BR")
    private val dia = SimpleDateFormat("dd/MM/yy", br)

    fun tamanho(bytes: Long): String {
        val mb = bytes / 1048576.0
        return if (mb >= 1024) String.format(br, "%.1f GB", mb / 1024) else String.format(br, "%.0f MB", mb)
    }

    fun duracao(s: Int): String {
        val h = s / 3600
        val m = s % 3600 / 60
        val seg = s % 60
        return if (h > 0) String.format(br, "%d:%02d:%02d", h, m, seg) else String.format(br, "%d:%02d", m, seg)
    }

    fun data(unix: Int): String = dia.format(Date(unix * 1000L))
}
