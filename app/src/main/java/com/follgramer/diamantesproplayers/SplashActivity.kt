package com.follgramer.diamantesproplayers

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // IMPORTANTE: Configurar el contenido ANTES de configurar la ventana
        setContentView(R.layout.activity_splash)

        // Configurar ventana para pantalla completa
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Configurar barra de estado (opcional)
        try {
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } catch (e: Exception) {
            // Si falla la configuración de la barra de estado, continuar sin problemas
            e.printStackTrace()
        }

        // Retraso de 3 segundos para mostrar la animación de carga
        Handler(Looper.getMainLooper()).postDelayed({
            // Iniciar MainActivity después del splash
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // Cerrar SplashActivity para que no vuelva atrás

            // Usar la transición apropiada según la versión de Android
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // Para Android 14+ usar overrideActivityTransition
                    overrideActivityTransition(
                        OVERRIDE_TRANSITION_OPEN,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                    )
                } else {
                    // Para versiones anteriores usar overridePendingTransition
                    @Suppress("DEPRECATION")
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }
            } catch (e: Exception) {
                // Si falla la transición, continuar sin animación
                e.printStackTrace()
            }
        }, 3000) // 3 segundos de splash screen
    }

    // Agregar @Deprecated para suprimir el warning
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // No llamar a super.onBackPressed() para deshabilitar el botón atrás
        // durante el splash screen
    }
}