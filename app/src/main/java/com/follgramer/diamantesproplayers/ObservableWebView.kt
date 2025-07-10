package com.follgramer.diamantesproplayers

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView
import android.util.Log

/**
 * WebView personalizada para la aplicación Diamantes Pro Players
 * Extiende WebView con funcionalidades observables adicionales
 */
class ObservableWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    init {
        Log.d("ObservableWebView", "ObservableWebView inicializada correctamente")
    }

    // Aquí puedes agregar funcionalidades adicionales si las necesitas
    // Por ejemplo, callbacks personalizados, listeners, etc.

    // Método para limpiar recursos cuando sea necesario
    override fun destroy() {
        Log.d("ObservableWebView", "ObservableWebView destruida")
        super.destroy()
    }
}