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
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.initialization.InitializationStatus
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewAssetLoader.AssetsPathHandler
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Logger
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import android.os.Handler
import android.os.Looper
import android.os.Bundle as AndroidBundle
import com.google.ads.mediation.admob.AdMobAdapter
import android.webkit.ConsoleMessage

class MainActivity : AppCompatActivity() {

    private lateinit var webView: ObservableWebView
    private lateinit var webAppInterface: WebAppInterface
    private lateinit var adView: AdView
    private var mRewardedAdForTask: RewardedAd? = null
    private var mRewardedAdForSpins: RewardedAd? = null
    private var retryCountTask = 0
    private var retryCountSpins = 0
    private val maxRetries = 3
    private val retryDelayMs = 5000L

    private val rewardedAdTaskUnitId = BuildConfig.REWARDED_AD_TASK_ID
    private val rewardedAdSpinsUnitId = BuildConfig.REWARDED_AD_SPINS_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseDatabase.getInstance().setLogLevel(Logger.Level.DEBUG)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        adView = findViewById(R.id.adView)

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
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            javaScriptCanOpenWindowsAutomatically = false // Evita ventanas emergentes no deseadas
        }

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/assets/", AssetsPathHandler(this))
            .build()

        webAppInterface = WebAppInterface(this, webView, this)
        webView.addJavascriptInterface(webAppInterface, "Android")
        Log.d("MainActivity", "JavaScript interface 'Android' added to WebView.")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                Log.d("MainActivity", "Interceptando solicitud: ${request.url}")
                val response = assetLoader.shouldInterceptRequest(request.url)
                if (response == null) {
                    Log.e("MainActivity", "No se pudo cargar el recurso: ${request.url}")
                }
                return response
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("MainActivity", "WebView página terminada de cargar: $url")
                if (url?.startsWith("https://appassets.androidplatform.net/assets/index.html") == true) {
                    lifecycleScope.launch {
                        val jsFunctionDefinitionsCode = """
                            window.showTicketTransferPrompt = function(rewardAmount, currentId) {
                                console.log('showTicketTransferPrompt called with:', rewardAmount, currentId);
                                
                                if (!currentId || currentId === 'null' || currentId === '') {
                                    Swal.fire('Error', 'ID de jugador no válida para completar la recompensa.', 'error');
                                    return;
                                }
                                
                                if (window.Android && window.Android.addTickets) {
                                    window.Android.addTickets(rewardAmount);
                                    
                                    Swal.fire({
                                        title: '🎉 ¡Video Completado!',
                                        html: '<p>¡Has ganado <strong style="color: #ffd43b;">' + rewardAmount + ' tickets</strong>!</p>',
                                        icon: 'success',
                                        timer: 2500,
                                        showConfirmButton: false
                                    });
                                    
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
                            
                            if (typeof Android !== 'undefined' && Android.showToast === undefined) {
                                Android.showToast = function(toast) { 
                                    console.log('Toast:', toast); 
                                };
                            }
                            
                            console.log('JavaScript bridge functions loaded successfully.');
                        """.trimIndent()

                        webView.evaluateJavascript(jsFunctionDefinitionsCode, { result ->
                            Log.d("MainActivity", "JavaScript injection result: $result")
                        })
                    }

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
                val errorMessage = when (error?.errorCode) {
                    WebViewClient.ERROR_HOST_LOOKUP -> "No se pudo resolver el host. Verifica tu conexión a internet."
                    WebViewClient.ERROR_CONNECT -> "Error de conexión. Asegúrate de estar conectado a internet."
                    WebViewClient.ERROR_TIMEOUT -> "Tiempo de espera agotado. Intenta de nuevo."
                    WebViewClient.ERROR_FILE_NOT_FOUND -> "Archivo no encontrado. Verifica que 'index.html' esté en la carpeta assets."
                    else -> "Error al cargar la página: ${error?.description}. Verifica tu conexión a internet."
                }
                webView.evaluateJavascript(
                    """
                    if (window.Swal) {
                        Swal.fire('Error', '$errorMessage', 'error');
                    } else {
                        console.error('SweetAlert2 no está disponible para mostrar el error: $errorMessage');
                    }
                    """.trimIndent(),
                    null
                )
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    Log.d("WebViewConsole", "${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}")
                }
                return true
            }
        }

        try {
            webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
            Log.d("MainActivity", "WebView intentando cargar URL: https://appassets.androidplatform.net/assets/index.html")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error al cargar URL en WebView: ${e.message}")
            webView.evaluateJavascript(
                """
                if (window.Swal) {
                    Swal.fire('Error', 'Error al inicializar la página. Verifica que index.html esté en assets.', 'error');
                } else {
                    console.error('SweetAlert2 no está disponible para mostrar el error de inicialización.');
                }
                """.trimIndent(),
                null
            )
        }

        requestConsentAndInitializeAds()

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

    private fun requestConsentAndInitializeAds() {
        Log.d("AdMob", "Iniciando solicitud de consentimiento")
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        val consentInformation = UserMessagingPlatform.getConsentInformation(this)
        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                Log.d("AdMob", "Información de consentimiento obtenida. ConsentStatus: ${consentInformation.consentStatus}")
                if (consentInformation.isConsentFormAvailable) {
                    UserMessagingPlatform.loadConsentForm(
                        this,
                        { consentForm ->
                            Log.d("AdMob", "Formulario de consentimiento cargado, mostrando...")
                            consentForm.show(this) { formError ->
                                if (formError == null && consentInformation.consentStatus == ConsentInformation.ConsentStatus.OBTAINED) {
                                    Log.d("AdMob", "Consentimiento otorgado, inicializando AdMob")
                                    initializeAdMob()
                                } else {
                                    Log.e("AdMob", "Error al mostrar formulario de consentimiento: ${formError?.message}")
                                    initializeAdMob()
                                }
                            }
                        },
                        { formError ->
                            Log.e("AdMob", "Error cargando formulario de consentimiento: ${formError.message}")
                            initializeAdMob()
                        }
                    )
                } else {
                    Log.d("AdMob", "No hay formulario de consentimiento disponible, inicializando AdMob")
                    initializeAdMob()
                }
            },
            { requestError ->
                Log.e("AdMob", "Error solicitando información de consentimiento: ${requestError.message}")
                initializeAdMob()
            }
        )
    }

    private fun initializeAdMob() {
        MobileAds.initialize(this, object : OnInitializationCompleteListener {
            override fun onInitializationComplete(status: InitializationStatus) {
                Log.d("AdMob", "AdMob inicializado: ${status.adapterStatusMap}")
                loadRewardedAdForTask()
                loadRewardedAdForSpins()
            }
        })
    }

    fun loadBannerAd() {
        val extras = AndroidBundle()
        if (!webAppInterface.hasConsented()) {
            extras.putString("npa", "1")
        }
        val adRequest = AdRequest.Builder()
            .addNetworkExtrasBundle(com.google.ads.mediation.admob.AdMobAdapter::class.java, extras)
            .build()
        adView.loadAd(adRequest)
        Log.d("AdMob", "Banner ad loaded after user consent.")
    }

    private fun loadRewardedAdForTask() {
        if (retryCountTask >= maxRetries) {
            Log.d("AdMob", "Máximo de reintentos alcanzado para RewardedAdForTask.")
            webView.evaluateJavascript(
                """
                if (window.Swal) {
                    Swal.fire('Error', 'No se pudo cargar el anuncio. Verifica tu conexión e intenta de nuevo más tarde.', 'error');
                } else {
                    console.error('SweetAlert2 no está disponible para mostrar el error.');
                }
                const taskButton = document.getElementById('task-button');
                if (taskButton) {
                    taskButton.textContent = '📺 Ver un Video (+20 Tickets)';
                    taskButton.style.background = 'var(--purple-accent)';
                    taskButton.disabled = false;
                }
                """.trimIndent(), null
            )
            return
        }

        val extras = AndroidBundle()
        if (!webAppInterface.hasConsented()) {
            extras.putString("npa", "1")
        }
        val adRequest = AdRequest.Builder()
            .addNetworkExtrasBundle(com.google.ads.mediation.admob.AdMobAdapter::class.java, extras)
            .build()
        RewardedAd.load(this, rewardedAdTaskUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d("AdMob", "RewardedAdForTask falló al cargar: ${adError.message}, code: ${adError.code}")
                mRewardedAdForTask = null
                retryCountTask++
                val errorMessage = when (adError.code) {
                    AdRequest.ERROR_CODE_NO_FILL -> "No hay anuncios disponibles en este momento."
                    AdRequest.ERROR_CODE_NETWORK_ERROR -> "Error de red. Verifica tu conexión a internet."
                    else -> "Error al cargar el anuncio. Intenta de nuevo."
                }
                webView.evaluateJavascript(
                    """
                    if (window.Swal) {
                        Swal.fire('Error', '$errorMessage', 'error');
                    } else {
                        console.error('SweetAlert2 no está disponible para mostrar el error: $errorMessage');
                    }
                    const taskButton = document.getElementById('task-button');
                    if (taskButton) {
                        taskButton.textContent = '📺 Ver un Video (+20 Tickets)';
                        taskButton.style.background = 'var(--purple-accent)';
                        taskButton.disabled = false;
                    }
                    """.trimIndent(), null
                )
                Handler(Looper.getMainLooper()).postDelayed({
                    loadRewardedAdForTask()
                }, retryDelayMs)
            }

            override fun onAdLoaded(rewardedAd: RewardedAd) {
                Log.d("AdMob", "RewardedAdForTask cargado correctamente.")
                mRewardedAdForTask = rewardedAd
                retryCountTask = 0
                mRewardedAdForTask?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.d("AdMob", "RewardedAdForTask falló al mostrar: ${adError.message}")
                        mRewardedAdForTask = null
                        loadRewardedAdForTask()
                        webView.evaluateJavascript(
                            """
                            if (window.Swal) {
                                Swal.fire('Error', 'El video no se pudo mostrar. Intenta de nuevo.', 'error');
                            } else {
                                console.error('SweetAlert2 no está disponible para mostrar el error.');
                            }
                            const taskButton = document.getElementById('task-button');
                            if (taskButton) {
                                taskButton.textContent = '📺 Ver un Video (+20 Tickets)';
                                taskButton.style.background = 'var(--purple-accent)';
                                taskButton.disabled = false;
                            }
                            """.trimIndent(), null
                        )
                    }

                    override fun onAdDismissedFullScreenContent() {
                        Log.d("AdMob", "RewardedAdForTask fue cerrado.")
                        mRewardedAdForTask = null
                        loadRewardedAdForTask()
                        webView.evaluateJavascript(
                            """
                            const taskButton = document.getElementById('task-button');
                            if (taskButton && taskButton.disabled) {
                                taskButton.textContent = '📺 Ver un Video (+20 Tickets)';
                                taskButton.style.background = 'var(--purple-accent)';
                                taskButton.disabled = false;
                            }
                            """.trimIndent(), null
                        )
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d("AdMob", "RewardedAdForTask mostrado.")
                    }
                }
            }
        })
    }

    private fun loadRewardedAdForSpins() {
        if (retryCountSpins >= maxRetries) {
            Log.d("AdMob", "Máximo de reintentos alcanzado para RewardedAdForSpins.")
            webView.evaluateJavascript(
                """
                if (window.Swal) {
                    Swal.fire('Error', 'No se pudo cargar el anuncio. Verifica tu conexión e intenta de nuevo más tarde.', 'error');
                } else {
                    console.error('SweetAlert2 no está disponible para mostrar el error.');
                }
                const getSpinsButton = document.getElementById('get-spins-button');
                if (getSpinsButton) {
                    getSpinsButton.textContent = '📺 Ver Video para Giros (+10)';
                    getSpinsButton.style.background = 'var(--gold-accent)';
                    getSpinsButton.disabled = false;
                }
                """.trimIndent(), null
            )
            return
        }

        val extras = AndroidBundle()
        if (!webAppInterface.hasConsented()) {
            extras.putString("npa", "1")
        }
        val adRequest = AdRequest.Builder()
            .addNetworkExtrasBundle(com.google.ads.mediation.admob.AdMobAdapter::class.java, extras)
            .build()
        RewardedAd.load(this, rewardedAdSpinsUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d("AdMob", "RewardedAdForSpins falló al cargar: ${adError.message}, code: ${adError.code}")
                mRewardedAdForSpins = null
                retryCountSpins++
                val errorMessage = when (adError.code) {
                    AdRequest.ERROR_CODE_NO_FILL -> "No hay anuncios disponibles en este momento."
                    AdRequest.ERROR_CODE_NETWORK_ERROR -> "Error de red. Verifica tu conexión a internet."
                    else -> "Error al cargar el anuncio. Intenta de nuevo."
                }
                webView.evaluateJavascript(
                    """
                    if (window.Swal) {
                        Swal.fire('Error', '$errorMessage', 'error');
                    } else {
                        console.error('SweetAlert2 no está disponible para mostrar el error: $errorMessage');
                    }
                    const getSpinsButton = document.getElementById('get-spins-button');
                    if (getSpinsButton) {
                        getSpinsButton.textContent = '📺 Ver Video para Giros (+10)';
                        getSpinsButton.style.background = 'var(--gold-accent)';
                        getSpinsButton.disabled = false;
                    }
                    """.trimIndent(), null
                )
                Handler(Looper.getMainLooper()).postDelayed({
                    loadRewardedAdForSpins()
                }, retryDelayMs)
            }

            override fun onAdLoaded(rewardedAd: RewardedAd) {
                Log.d("AdMob", "RewardedAdForSpins cargado correctamente.")
                mRewardedAdForSpins = rewardedAd
                retryCountSpins = 0
                mRewardedAdForSpins?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.d("AdMob", "RewardedAdForSpins falló al mostrar: ${adError.message}")
                        mRewardedAdForSpins = null
                        loadRewardedAdForSpins()
                        webView.evaluateJavascript(
                            """
                            if (window.Swal) {
                                Swal.fire('Error', 'El video para giros no se pudo mostrar. Intenta de nuevo.', 'error');
                            } else {
                                console.error('SweetAlert2 no está disponible para mostrar el error.');
                            }
                            const getSpinsButton = document.getElementById('get-spins-button');
                            if (getSpinsButton) {
                                getSpinsButton.textContent = '📺 Ver Video para Giros (+10)';
                                getSpinsButton.style.background = 'var(--gold-accent)';
                                getSpinsButton.disabled = false;
                            }
                            """.trimIndent(), null
                        )
                    }

                    override fun onAdDismissedFullScreenContent() {
                        Log.d("AdMob", "RewardedAdForSpins fue cerrado.")
                        mRewardedAdForSpins = null
                        loadRewardedAdForSpins()
                        webView.evaluateJavascript(
                            """
                            const getSpinsButton = document.getElementById('get-spins-button');
                            if (getSpinsButton && getSpinsButton.disabled) {
                                getSpinsButton.textContent = '📺 Ver Video para Giros (+10)';
                                getSpinsButton.style.background = 'var(--gold-accent)';
                                getSpinsButton.disabled = false;
                            }
                            """.trimIndent(), null
                        )
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d("AdMob", "RewardedAdForSpins mostrado.")
                    }
                }
            }
        })
    }

    fun requestRewardedAdForTask(rewardAmount: Int, currentId: String) {
        Log.d("AdMob", "requestRewardedAdForTask called with reward: $rewardAmount, currentId: $currentId")

        if (currentId.isEmpty() || currentId == "null") {
            webView.evaluateJavascript(
                """
                if (window.Swal) {
                    Swal.fire('Error', 'Configura tu ID de jugador primero.', 'warning');
                } else {
                    console.error('SweetAlert2 no está disponible para mostrar el error.');
                }
                const taskButton = document.getElementById('task-button');
                if (taskButton) {
                    taskButton.textContent = '📺 Ver un Video (+20 Tickets)';
                    taskButton.style.background = 'var(--purple-accent)';
                    taskButton.disabled = false;
                }
                """.trimIndent(), null
            )
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
            webView.evaluateJavascript(
                """
                if (window.Swal) {
                    Swal.fire('Error', 'El video no está listo. Intenta de nuevo en unos segundos.', 'error');
                } else {
                    console.error('SweetAlert2 no está disponible para mostrar el error.');
                }
                const taskButton = document.getElementById('task-button');
                if (taskButton) {
                    taskButton.textContent = '📺 Ver un Video (+20 Tickets)';
                    taskButton.style.background = 'var(--purple-accent)';
                    taskButton.disabled = false;
                }
                """.trimIndent(), null
            )
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
            webView.evaluateJavascript(
                """
                if (window.Swal) {
                    Swal.fire('Error', 'El video para giros no está listo. Intenta de nuevo en unos segundos.', 'error');
                } else {
                    console.error('SweetAlert2 no está disponible para mostrar el error.');
                }
                const getSpinsButton = document.getElementById('get-spins-button');
                if (getSpinsButton) {
                    getSpinsButton.textContent = '📺 Ver Video para Giros (+10)';
                    getSpinsButton.style.background = 'var(--gold-accent)';
                    getSpinsButton.disabled = false;
                }
                """.trimIndent(), null
            )
        }
    }

    override fun onDestroy() {
        if (::adView.isInitialized) {
            adView.destroy()
        }
        mRewardedAdForTask = null
        mRewardedAdForSpins = null
        super.onDestroy()
        webView.destroy()
    }

    override fun onPause() {
        if (::adView.isInitialized) {
            adView.pause()
        }
        super.onPause()
    }

    override fun onResume() {
        if (::adView.isInitialized) {
            adView.resume()
        }
        super.onResume()
    }
}