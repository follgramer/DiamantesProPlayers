package com.follgramer.diamantesproplayers

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.initialization.InitializationStatus
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var consentInformation: ConsentInformation
    private var consentForm: ConsentForm? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_splash)

        Log.d("SplashActivity", "SplashActivity iniciado")

        // Verificar si ya aceptó las políticas básicas
        val prefs = getSharedPreferences("DiamantesProPlayersPrefs", MODE_PRIVATE)
        val hasAcceptedPolicies = prefs.getBoolean("hasAcceptedPolicies", false)

        if (hasAcceptedPolicies) {
            // Ya aceptó políticas básicas, proceder con UMP
            initializeConsentProcess()
        } else {
            // Primera vez, mostrar políticas básicas
            showBasicPoliciesDialog()
        }
    }

    private fun showBasicPoliciesDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("🎮 Bienvenido a Diamantes Pro Players")
            .setMessage("""
                Para ofrecerte la mejor experiencia necesitamos tu consentimiento para:
                
                📊 Recopilar tu ID de Free Fire para sorteos
                📺 Mostrar anuncios opcionales para recompensas
                🔒 Procesar datos según nuestras políticas
                
                ¿Aceptas nuestros Términos y Política de Privacidad?
                
                Sin esta aceptación no podrás usar la aplicación.
            """.trimIndent())
            .setPositiveButton("✅ Acepto") { _, _ ->
                // Usuario aceptó políticas básicas
                saveBasicPoliciesAcceptance(true)
                initializeConsentProcess()
            }
            .setNegativeButton("❌ No acepto") { _, _ ->
                // Usuario rechazó, mostrar segundo modal
                showSecondChanceDialog()
            }
            .setCancelable(false)
            .create()

        dialog.show()
    }

    private fun showSecondChanceDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("⚠️ ¿Estás seguro?")
            .setMessage("""
                Sin aceptar las políticas NO puedes:
                
                ❌ Participar en sorteos de diamantes
                ❌ Ganar pases élite gratis
                ❌ Acumular tickets y recompensas
                ❌ Ver tu posición en rankings
                
                ✅ Aceptar te permite:
                🎁 Ganar premios reales de Free Fire
                🏆 Competir en clasificaciones
                📺 Ver anuncios SOLO si quieres (opcional)
                🔒 Tus datos están protegidos según GDPR
                
                ¿Cambias de opinión?
            """.trimIndent())
            .setPositiveButton("✅ Acepto ahora") { _, _ ->
                saveBasicPoliciesAcceptance(true)
                initializeConsentProcess()
            }
            .setNegativeButton("🚪 Cerrar app") { _, _ ->
                finish() // Cierra la aplicación
            }
            .setCancelable(false)
            .create()

        dialog.show()
    }

    private fun saveBasicPoliciesAcceptance(accepted: Boolean) {
        val prefs = getSharedPreferences("DiamantesProPlayersPrefs", MODE_PRIVATE)
        val editor = prefs.edit()

        // ✅ CORRECCIÓN: Guardar TODOS los permisos necesarios
        editor.putBoolean("hasAcceptedPolicies", accepted)

        if (accepted) {
            editor.putBoolean("canConnectFirebase", true)
            editor.putBoolean("adConsent", true)  // ← LÍNEA AGREGADA
            Log.d("SplashActivity", "Políticas básicas guardadas: $accepted")
            Log.d("SplashActivity", "Permitiendo conexión a Firebase")
            Log.d("SplashActivity", "Consentimiento básico de anuncios otorgado")
        } else {
            editor.putBoolean("canConnectFirebase", false)
            editor.putBoolean("adConsent", false)
            Log.d("SplashActivity", "Permisos denegados")
        }

        editor.apply()
    }

    private fun initializeConsentProcess() {
        Log.d("SplashActivity", "Iniciando proceso de consentimiento UMP")

        consentInformation = UserMessagingPlatform.getConsentInformation(this)

        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                Log.d("SplashActivity", "Información de consentimiento actualizada. Estado: ${consentInformation.consentStatus}")
                handleConsentStatus()
            },
            { formError ->
                Log.e("SplashActivity", "Error al solicitar información de consentimiento: ${formError.message}")
                // Continuar sin consentimiento UMP si hay error
                initializeAdsAndProceed(hasUMPConsent = false)
            }
        )
    }

    private fun handleConsentStatus() {
        when (consentInformation.consentStatus) {
            ConsentInformation.ConsentStatus.REQUIRED -> {
                Log.d("SplashActivity", "Consentimiento UMP requerido, cargando formulario")
                loadConsentForm()
            }
            ConsentInformation.ConsentStatus.OBTAINED -> {
                Log.d("SplashActivity", "Consentimiento UMP ya obtenido")
                initializeAdsAndProceed(hasUMPConsent = true)
            }
            ConsentInformation.ConsentStatus.NOT_REQUIRED -> {
                Log.d("SplashActivity", "Consentimiento UMP no requerido")
                initializeAdsAndProceed(hasUMPConsent = false)
            }
            else -> {
                Log.d("SplashActivity", "Estado de consentimiento UMP desconocido")
                initializeAdsAndProceed(hasUMPConsent = false)
            }
        }
    }

    private fun loadConsentForm() {
        if (consentInformation.isConsentFormAvailable) {
            UserMessagingPlatform.loadConsentForm(
                this,
                { form ->
                    Log.d("SplashActivity", "Formulario UMP cargado")
                    consentForm = form
                    showConsentFormIfRequired()
                },
                { formError ->
                    Log.e("SplashActivity", "Error al cargar formulario UMP: ${formError.message}")
                    initializeAdsAndProceed(hasUMPConsent = false)
                }
            )
        } else {
            Log.d("SplashActivity", "Formulario UMP no disponible")
            initializeAdsAndProceed(hasUMPConsent = false)
        }
    }

    private fun showConsentFormIfRequired() {
        consentForm?.show(this) { formError ->
            if (formError != null) {
                Log.e("SplashActivity", "Error al mostrar formulario UMP: ${formError.message}")
            } else {
                Log.d("SplashActivity", "Formulario UMP completado")
            }

            when (consentInformation.consentStatus) {
                ConsentInformation.ConsentStatus.OBTAINED -> {
                    Log.d("SplashActivity", "Consentimiento UMP otorgado")
                    saveUMPConsentStatus(true)
                    initializeAdsAndProceed(hasUMPConsent = true)
                }
                else -> {
                    Log.d("SplashActivity", "Consentimiento UMP no otorgado")
                    saveUMPConsentStatus(false)
                    initializeAdsAndProceed(hasUMPConsent = false)
                }
            }
        }
    }

    private fun saveUMPConsentStatus(hasConsented: Boolean) {
        val prefs = getSharedPreferences("DiamantesProPlayersPrefs", MODE_PRIVATE)
        prefs.edit().putBoolean("adConsent", hasConsented).apply()
        Log.d("SplashActivity", "Estado UMP guardado: $hasConsented")
    }

    private fun initializeAdsAndProceed(hasUMPConsent: Boolean) {
        Log.d("SplashActivity", "Inicializando AdMob con consentimiento: $hasUMPConsent")

        MobileAds.initialize(this) { initializationStatus ->
            Log.d("SplashActivity", "AdMob inicializado: ${initializationStatus.adapterStatusMap}")

            CoroutineScope(Dispatchers.Main).launch {
                delay(1500) // Splash de 1.5 segundos
                proceedToMainActivity()
            }
        }
    }

    private fun proceedToMainActivity() {
        Log.d("SplashActivity", "Procediendo a MainActivity")

        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()

        // Animación de transición suave (deprecated pero funcional)
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        consentForm = null
        super.onDestroy()
    }
}