package com.follgramer.diamantesproplayers

import android.os.Bundle
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebResourceError
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

// Importaciones necesarias para AdMob
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback

// WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler

// Firebase Logging
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Logger

class MainActivity : AppCompatActivity(), DefaultLifecycleObserver {

    private lateinit var webView: ObservableWebView
    private lateinit var webAppInterface: WebAppInterface
    private lateinit var adView: AdView
    private lateinit var appOpenAdManager: AppOpenAdManager

    // Anuncios
    private var mRewardedAdForTask: RewardedAd? = null
    private var mRewardedAdForSpins: RewardedAd? = null
    private var mInterstitialAd: InterstitialAd? = null

    // Control de tiempo para intersticiales
    private var lastInterstitialTime = 0L
    private val INTERSTITIAL_COOLDOWN = 30000L // 30 segundos entre intersticiales

    // IDs de anuncios de prueba - Cámbialos por tus IDs reales cuando publiques
    private val rewardedAdTaskUnitId = "ca-app-pub-3940256099942544/5224354917"
    private val rewardedAdSpinsUnitId = "ca-app-pub-3940256099942544/5224354917"
    private val interstitialAdUnitId = "ca-app-pub-3940256099942544/1033173712"
    private val appOpenAdUnitId = "ca-app-pub-3940256099942544/9257395921"

    override fun onCreate(savedInstanceState: Bundle?) {
        super<AppCompatActivity>.onCreate(savedInstanceState)

        // Configurar Firebase Database para debug
        FirebaseDatabase.getInstance().setLogLevel(Logger.Level.DEBUG)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)

        // Configuración e inicialización de AdMob
        MobileAds.initialize(this) { initializationStatus ->
            Log.d("AdMob", "AdMob inicializado: ${initializationStatus.adapterStatusMap}")
            // Cargar anuncios después de la inicialización
            loadRewardedAdForTask()
            loadRewardedAdForSpins()
            loadInterstitialAd()
        }

        // Inicializar App Open Ad Manager
        appOpenAdManager = AppOpenAdManager()

        // Registrar observer del ciclo de vida de la aplicación
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // Configurar banner ad
        adView = findViewById(R.id.adView)
        val adRequestBanner = AdRequest.Builder().build()
        adView.loadAd(adRequestBanner)

        // Configuración del WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }

        // Habilitar debug para WebView (solo en desarrollo)
        WebView.setWebContentsDebuggingEnabled(true)

        // Configurar AssetLoader para cargar archivos locales
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", AssetsPathHandler(this))
            .build()

        // Inicializar WebAppInterface
        webAppInterface = WebAppInterface(this, webView, this)
        webView.addJavascriptInterface(webAppInterface, "Android")
        Log.d("MainActivity", "JavaScript interface 'Android' added to WebView.")

        // Configurar WebViewClient
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url?.startsWith("https://appassets.androidplatform.net/assets/index.html") == true) {
                    Log.d("MainActivity", "WebView page finished loading: $url")

                    lifecycleScope.launch {
                        // Inyectar funciones JavaScript necesarias
                        val jsFunctionDefinitionsCode = """
                            // Función para mostrar prompt de transferencia de tickets después de ver video
                            window.showTicketTransferPrompt = function(rewardAmount, currentId) {
                                console.log('showTicketTransferPrompt called with:', rewardAmount, currentId);
                                
                                if (!currentId || currentId === 'null' || currentId === '') {
                                    Swal.fire('Error', 'ID de jugador no válida para completar la recompensa.', 'error');
                                    return;
                                }
                                
                                // Agregar tickets directamente
                                if (window.Android && window.Android.addTickets) {
                                    window.Android.addTickets(rewardAmount);
                                    
                                    // Mostrar mensaje de éxito
                                    Swal.fire({
                                        title: '🎉 ¡Video Completado!',
                                        html: '<p>¡Has ganado <strong style="color: #ffd43b;">' + rewardAmount + ' tickets</strong>!</p>',
                                        icon: 'success',
                                        timer: 2500,
                                        showConfirmButton: false
                                    });
                                    
                                    // Restablecer botón de tarea
                                    const taskButton = document.getElementById('task-button');
                                    if (taskButton) {
                                        taskButton.textContent = '📺 Ver un Video (+20 Tickets)';
                                        taskButton.style.background = 'var(--purple-accent)';
                                        taskButton.disabled = false;
                                    }
                                } else {
                                    console.error('Android interface not available');
                                    Swal.fire('Error', 'No se pudo agregar los tickets.', 'error');
                                }
                            };
                            
                            // Función de respaldo para showToast si no está definida
                            if (typeof Android !== 'undefined' && Android.showToast === undefined) {
                                Android.showToast = function(toast) { 
                                    console.log('Toast:', toast); 
                                };
                            }
                            
                            console.log('JavaScript bridge functions loaded successfully.');
                        """.trimIndent()

                        webView.evaluateJavascript(jsFunctionDefinitionsCode, null)
                        Log.d("MainActivity", "JavaScript bridge functions injected AFTER page finished.")
                    }

                    // Obtener datos iniciales después de que la página cargue
                    webAppInterface.getInitialData()
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                Log.e("MainActivity", "WebView error: ${error?.errorCode} - ${error?.description} at ${request?.url}")
            }
        }

        // Configurar WebChromeClient para mejor soporte de JavaScript
        webView.webChromeClient = WebChromeClient()

        // Cargar la página principal
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        Log.d("MainActivity", "WebView loaded URL: https://appassets.androidplatform.net/assets/index.html")

        // Configurar manejo del botón de retroceso
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    // --- App Open Ad Implementation ---
    override fun onStart(owner: LifecycleOwner) {
        super<DefaultLifecycleObserver>.onStart(owner)
        appOpenAdManager.showAdIfAvailable(this)
    }

    // --- Métodos para cargar anuncios ---

    private fun loadRewardedAdForTask() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, rewardedAdTaskUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d("AdMob", "RewardedAdForTask falló al cargar: ${adError.message}")
                mRewardedAdForTask = null
            }

            override fun onAdLoaded(rewardedAd: RewardedAd) {
                Log.d("AdMob", "RewardedAdForTask cargado correctamente.")
                mRewardedAdForTask = rewardedAd
                mRewardedAdForTask?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.d("AdMob", "RewardedAdForTask falló al mostrar: ${adError.message}")
                        mRewardedAdForTask = null
                        loadRewardedAdForTask()
                        webView.evaluateJavascript("""
                            const taskButton = document.getElementById('task-button');
                            if (taskButton) {
                                taskButton.textContent = '📺 Ver un Video (+20 Tickets)';
                                taskButton.style.background = 'var(--purple-accent)';
                                taskButton.disabled = false;
                            }
                            Swal.fire('Error', 'El video no se pudo mostrar. Intenta de nuevo.', 'error');
                        """.trimIndent(), null)
                    }

                    override fun onAdDismissedFullScreenContent() {
                        Log.d("AdMob", "RewardedAdForTask fue cerrado.")
                        mRewardedAdForTask = null
                        loadRewardedAdForTask()
                        webView.evaluateJavascript("""
                            const taskButton = document.getElementById('task-button');
                            if (taskButton && taskButton.disabled) {
                                taskButton.textContent = '📺 Ver un Video (+20 Tickets)';
                                taskButton.style.background = 'var(--purple-accent)';
                                taskButton.disabled = false;
                            }
                        """.trimIndent(), null)
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d("AdMob", "RewardedAdForTask mostrado.")
                    }
                }
            }
        })
    }

    private fun loadRewardedAdForSpins() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(this, rewardedAdSpinsUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d("AdMob", "RewardedAdForSpins falló al cargar: ${adError.message}")
                mRewardedAdForSpins = null
            }

            override fun onAdLoaded(rewardedAd: RewardedAd) {
                Log.d("AdMob", "RewardedAdForSpins cargado correctamente.")
                mRewardedAdForSpins = rewardedAd
                mRewardedAdForSpins?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.d("AdMob", "RewardedAdForSpins falló al mostrar: ${adError.message}")
                        mRewardedAdForSpins = null
                        loadRewardedAdForSpins()
                        webView.evaluateJavascript("""
                            const getSpinsButton = document.getElementById('get-spins-button');
                            if (getSpinsButton) {
                                getSpinsButton.textContent = '📺 Ver Video para Giros (+10)';
                                getSpinsButton.style.background = 'var(--gold-accent)';
                                getSpinsButton.disabled = false;
                            }
                            Swal.fire('Error', 'El video para giros no se pudo mostrar. Intenta de nuevo.', 'error');
                        """.trimIndent(), null)
                    }

                    override fun onAdDismissedFullScreenContent() {
                        Log.d("AdMob", "RewardedAdForSpins fue cerrado.")
                        mRewardedAdForSpins = null
                        loadRewardedAdForSpins()
                        webView.evaluateJavascript("""
                            const getSpinsButton = document.getElementById('get-spins-button');
                            if (getSpinsButton && getSpinsButton.disabled) {
                                getSpinsButton.textContent = '📺 Ver Video para Giros (+10)';
                                getSpinsButton.style.background = 'var(--gold-accent)';
                                getSpinsButton.disabled = false;
                            }
                        """.trimIndent(), null)
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d("AdMob", "RewardedAdForSpins mostrado.")
                    }
                }
            }
        })
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, interstitialAdUnitId, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d("AdMob", "Interstitial falló al cargar: ${adError.message}")
                mInterstitialAd = null
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d("AdMob", "Interstitial cargado correctamente.")
                mInterstitialAd = interstitialAd
                mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.d("AdMob", "Interstitial falló al mostrar: ${adError.message}")
                        mInterstitialAd = null
                        loadInterstitialAd()
                    }

                    override fun onAdDismissedFullScreenContent() {
                        Log.d("AdMob", "Interstitial fue cerrado.")
                        mInterstitialAd = null
                        loadInterstitialAd()
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d("AdMob", "Interstitial mostrado.")
                    }
                }
            }
        })
    }

    // --- Métodos públicos para mostrar anuncios ---

    fun requestRewardedAdForTask(rewardAmount: Int, currentId: String) {
        Log.d("AdMob", "requestRewardedAdForTask called with reward: $rewardAmount, currentId: $currentId")

        if (currentId.isEmpty() || currentId == "null") {
            webView.evaluateJavascript("""
                Swal.fire('Error', 'Configura tu ID de jugador primero.', 'warning');
                const taskButton = document.getElementById('task-button');
                if (taskButton) {
                    taskButton.textContent = '📺 Ver un Video (+20 Tickets)';
                    taskButton.style.background = 'var(--purple-accent)';
                    taskButton.disabled = false;
                }
            """.trimIndent(), null)
            return
        }

        if (mRewardedAdForTask != null) {
            mRewardedAdForTask?.show(this) { rewardItem ->
                Log.d("AdMob", "Usuario recompensado (tarea): ${rewardItem.amount} ${rewardItem.type}")
                webView.evaluateJavascript("window.showTicketTransferPrompt($rewardAmount, '$currentId');", null)
            }
            Log.d("AdMob", "Mostrando RewardedAd para tarea.")
        } else {
            Log.d("AdMob", "RewardedAd para tarea no está listo. Cargando uno nuevo.")
            loadRewardedAdForTask()
            webView.evaluateJavascript("""
                Swal.fire('Error', 'El video no está listo. Intenta de nuevo en unos segundos.', 'error');
                const taskButton = document.getElementById('task-button');
                if (taskButton) {
                    taskButton.textContent = '📺 Ver un Video (+20 Tickets)';
                    taskButton.style.background = 'var(--purple-accent)';
                    taskButton.disabled = false;
                }
            """.trimIndent(), null)
        }
    }

    fun requestRewardedAdForSpins(spinsAmount: Int) {
        Log.d("AdMob", "requestRewardedAdForSpins called with amount: $spinsAmount spins")

        if (mRewardedAdForSpins != null) {
            mRewardedAdForSpins?.show(this) { rewardItem ->
                Log.d("AdMob", "Usuario recompensado (giros): ${spinsAmount} giros")
                webView.evaluateJavascript("window.onSpinsRewarded($spinsAmount);", null)
            }
            Log.d("AdMob", "Mostrando RewardedAd para giros.")
        } else {
            Log.d("AdMob", "RewardedAd para giros no está listo. Cargando uno nuevo.")
            loadRewardedAdForSpins()
            webView.evaluateJavascript("""
                const getSpinsButton = document.getElementById('get-spins-button');
                if (getSpinsButton) {
                    getSpinsButton.textContent = '📺 Ver Video para Giros (+10)';
                    getSpinsButton.style.background = 'var(--gold-accent)';
                    getSpinsButton.disabled = false;
                }
                Swal.fire('Error', 'El video para giros no está listo. Intenta de nuevo en unos segundos.', 'error');
            """.trimIndent(), null)
        }
    }

    // Función para mostrar intersticiales con control de frecuencia
    fun showInterstitialWithCooldown() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastInterstitialTime < INTERSTITIAL_COOLDOWN) {
            Log.d("AdMob", "Interstitial en cooldown, no se mostrará")
            return
        }

        if (mInterstitialAd != null) {
            mInterstitialAd?.show(this)
            lastInterstitialTime = currentTime
            Log.d("AdMob", "Mostrando Interstitial")
        } else {
            Log.d("AdMob", "Interstitial no está listo")
            loadInterstitialAd()
        }
    }

    // --- App Open Ad Manager ---
    private inner class AppOpenAdManager {
        private var appOpenAd: AppOpenAd? = null
        private var isLoadingAd = false
        private var isShowingAd = false
        private var loadTime: Long = 0

        init {
            loadAd()
        }

        private fun loadAd() {
            if (isLoadingAd || isAdAvailable()) {
                return
            }

            isLoadingAd = true
            val request = AdRequest.Builder().build()
            AppOpenAd.load(
                this@MainActivity,
                appOpenAdUnitId,
                request,
                object : AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        Log.d("AdMob", "App Open Ad cargado")
                        appOpenAd = ad
                        isLoadingAd = false
                        loadTime = System.currentTimeMillis()
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.d("AdMob", "App Open Ad falló: ${loadAdError.message}")
                        isLoadingAd = false
                    }
                }
            )
        }

        private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
            val dateDifference = System.currentTimeMillis() - loadTime
            val numMilliSecondsPerHour: Long = 3600000
            return dateDifference < numMilliSecondsPerHour * numHours
        }

        private fun isAdAvailable(): Boolean {
            return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
        }

        fun showAdIfAvailable(activity: MainActivity) {
            if (isShowingAd) {
                Log.d("AdMob", "App Open Ad ya se está mostrando")
                return
            }

            if (!isAdAvailable()) {
                Log.d("AdMob", "App Open Ad no disponible")
                loadAd()
                return
            }

            appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("AdMob", "App Open Ad cerrado")
                    appOpenAd = null
                    isShowingAd = false
                    loadAd()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.d("AdMob", "App Open Ad falló al mostrar: ${adError.message}")
                    appOpenAd = null
                    isShowingAd = false
                    loadAd()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d("AdMob", "App Open Ad mostrado")
                    isShowingAd = true
                }
            }

            isShowingAd = true
            appOpenAd?.show(activity)
        }
    }

    override fun onDestroy() {
        if (::adView.isInitialized) {
            adView.destroy()
        }

        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)

        // Limpiar WebAppInterface
        if (::webAppInterface.isInitialized) {
            webAppInterface.cleanup()
        }

        super<AppCompatActivity>.onDestroy()
        webView.destroy()
    }

    override fun onPause() {
        if (::adView.isInitialized) {
            adView.pause()
        }
        super<AppCompatActivity>.onPause()
    }

    override fun onResume() {
        if (::adView.isInitialized) {
            adView.resume()
        }
        super<AppCompatActivity>.onResume()
    }
}