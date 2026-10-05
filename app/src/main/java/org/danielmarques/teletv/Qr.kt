package org.danielmarques.teletv

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object Qr {
    fun gerar(texto: String, lado: Int = 520): Bitmap {
        val m = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, lado, lado)
        val px = IntArray(lado * lado) { i -> if (m[i % lado, i / lado]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(px, lado, lado, Bitmap.Config.RGB_565)
    }
}
