package com.follgramer.diamantesproplayers

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView

/**
 * WebView personalizado que permite observación de eventos
 * Extiende la funcionalidad básica de WebView para nuestra aplicación
 */
class ObservableWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    private var onScrollChangedListener: OnScrollChangedListener? = null

    interface OnScrollChangedListener {
        fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int)
    }

    fun setOnScrollChangedListener(listener: OnScrollChangedListener?) {
        this.onScrollChangedListener = listener
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrollChangedListener?.onScrollChanged(l, t, oldl, oldt)
    }

    // Métodos adicionales para optimización de AdMob si es necesario
    override fun pauseTimers() {
        super.pauseTimers()
    }

    override fun resumeTimers() {
        super.resumeTimers()
    }
}