package com.follgramer.diamantesproplayers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.google.android.gms.ads.*
import com.google.firebase.database.*
import com.google.firebase.functions.FirebaseFunctions
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WebAppInterface(
    private val context: Context,
    private val webView: ObservableWebView,
    private val mainActivity: MainActivity
) {

    private var functions = FirebaseFunctions.getInstance()
    private var database = FirebaseDatabase.getInstance()
    private var usersRef = database.getReference("users")
    private var winnersRef = database.getReference("winners")
    private var sharedPrefs = context.getSharedPreferences("DiamantesProPlayersPrefs", Context.MODE_PRIVATE)

    private var currentUserData: UserData? = null
    private var usersListener: ValueEventListener? = null
    private var privateMessageListener: ValueEventListener? = null

    // La variable activeBanners ha sido eliminada.

    init {
        loadInitialData()
        setupFirebaseListeners()
    }

    private fun loadInitialData() {
        try {
            val savedPlayerId = sharedPrefs.getString("ff_player_id", null)
            if (savedPlayerId != null) {
                loadUser(savedPlayerId)
            }
        } catch (e: Exception) {
            Log.e("AppInterface", "Error loading initial data: ${e.message}")
        }
    }

    data class UserData(
        var playerId: String = "",
        var tickets: Long = 0,
        var passes: Long = 0
    )

    data class WinnerData(
        val winnerId: String = "",
        val prize: String = "",
        val timestamp: Long = System.currentTimeMillis(),
        val date: String? = null
    )

    @JavascriptInterface
    fun getInitialData() {
        Log.d("AppInterface", "getInitialData() called from JavaScript.")
        try {
            val playerId = sharedPrefs.getString("ff_player_id", null)
            if (playerId != null) {
                val userData = currentUserData
                if (userData != null) {
                    updateWebViewUserUI(userData.playerId, userData.tickets, userData.passes)
                } else {
                    loadUser(playerId)
                }
            } else {
                updateWebViewUserUI(null, 0, 0)
            }
        } catch (e: Exception) {
            Log.e("AppInterface", "Error in getInitialData: ${e.message}")
        }
    }

    @JavascriptInterface
    fun getPlayerId(): String? {
        return try {
            val playerId = sharedPrefs.getString("ff_player_id", null)
            Log.d("AppInterface", "getPlayerId() called from JavaScript. Returning: $playerId")
            playerId
        } catch (e: Exception) {
            Log.e("AppInterface", "Error getting playerId: ${e.message}")
            null
        }
    }

    @JavascriptInterface
    fun savePlayerId(newPlayerId: String) {
        Log.d("AppInterface", "savePlayerId() called from JavaScript with ID: $newPlayerId")

        try {
            val editor = sharedPrefs.edit()
            editor.putString("ff_player_id", newPlayerId)
            editor.apply()

            initializeUser(newPlayerId)

            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "ID de jugador guardado: $newPlayerId", Toast.LENGTH_SHORT).show()
            }

            Log.d("AppInterface", "Player ID saved and user initialized.")
        } catch (e: Exception) {
            Log.e("AppInterface", "Error saving playerId: ${e.message}")
        }
    }

    // --- FUNCIÓN REEMPLAZADA ---
    @JavascriptInterface
    fun showInterstitialBeforeSection(sectionName: String) {
        Log.d("AppInterface", "showInterstitialBeforeSection() called for section: $sectionName")

        try {
            when (sectionName) {
                "leaderboard", "winners", "tasks" -> {
                    mainActivity.runOnUiThread {
                        mainActivity.showInterstitialWithCooldown()
                    }
                }
                else -> {
                    Log.d("AppInterface", "No interstitial needed for section: $sectionName")
                }
            }
        } catch (e: Exception) {
            Log.e("AppInterface", "Error showing interstitial: ${e.message}")
        }
    }

    // --- FUNCIÓN REEMPLAZADA (SIMPLIFICADA) ---
    @JavascriptInterface
    fun checkAdMobAvailability(): Boolean {
        return true // AdMob siempre disponible para intersticiales y rewarded ads
    }

    // --- TODAS LAS FUNCIONES DE BANNER HAN SIDO ELIMINADAS ---
    // - createAdMobBanner
    // - createBannerInternal
    // - notifyBannerLoaded
    // - notifyBannerFailed
    // - onSectionChanged
    // - destroyAdMobBanner
    // - destroyAllAdMobBanners
    // - getActiveBannersCount

    private fun initializeUser(playerId: String) {
        Log.d("AppInterface", "Initializing user: $playerId")

        try {
            if (playerId.isBlank() || playerId.length < 5) {
                Log.w("AppInterface", "Invalid playerId provided: $playerId")
                CoroutineScope(Dispatchers.Main).launch {
                    webView.evaluateJavascript("Swal.fire('Error', 'ID de jugador no válido.', 'error');", null)
                }
                return
            }

            val data = hashMapOf("playerId" to playerId)

            functions.getHttpsCallable("initializeUser")
                .call(data)
                .addOnSuccessListener { result ->
                    Log.d("AppInterface", "User initialized successfully: ${result.data}")
                    loadUser(playerId)
                }
                .addOnFailureListener { e ->
                    Log.e("AppInterface", "Error initializing user: ${e.message}", e)
                    loadUser(playerId)
                }
        } catch (e: Exception) {
            Log.e("AppInterface", "Error in initializeUser: ${e.message}")
        }
    }

    @JavascriptInterface
    fun addTickets(amount: Int) {
        Log.d("AppInterface", "addTickets() called with amount: $amount")

        try {
            val playerId = sharedPrefs.getString("ff_player_id", null)
            if (playerId.isNullOrEmpty() || playerId.length < 5) {
                CoroutineScope(Dispatchers.Main).launch {
                    webView.evaluateJavascript("Swal.fire('Error', 'Configura tu ID de jugador primero.', 'warning');", null)
                }
                return
            }

            val data = hashMapOf("playerId" to playerId, "amount" to amount)

            functions.getHttpsCallable("addTickets")
                .call(data)
                .addOnSuccessListener { httpsCallableResult ->
                    Log.d("AppInterface", "addTickets successful: ${httpsCallableResult.data}")

                    val updatedUserResult = httpsCallableResult.data as? Map<*, *>
                    val userMap = updatedUserResult?.get("user") as? Map<*, *>

                    if (userMap != null) {
                        val tickets = (userMap["tickets"] as? Number)?.toLong() ?: 0L
                        val passes = (userMap["passes"] as? Number)?.toLong() ?: 0L
                        val pId = userMap["playerId"] as? String ?: ""
                        updateWebViewUserUI(pId, tickets, passes)

                        val cfMessage = updatedUserResult["message"] as? String
                        if (cfMessage != null && cfMessage.contains("pase", ignoreCase = true)) {
                            CoroutineScope(Dispatchers.Main).launch {
                                webView.evaluateJavascript("""
                                    Swal.fire({
                                        title: '🎉 ¡PASE GANADO!',
                                        html: '<p>¡Felicidades, has ganado un pase de sorteo!</p>',
                                        icon: 'success',
                                        timer: 3000,
                                        showConfirmButton: false
                                    });
                                """.trimIndent(), null)
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("AppInterface", "Error in addTickets: ${e.message}")
                    CoroutineScope(Dispatchers.Main).launch {
                        webView.evaluateJavascript("Swal.fire('Error', 'Error de conexión.', 'error');", null)
                    }
                }
        } catch (e: Exception) {
            Log.e("AppInterface", "Exception in addTickets: ${e.message}")
        }
    }

    // --- FUNCIÓN REEMPLAZADA ---
    @JavascriptInterface
    fun requestRewardedAdForTask(rewardAmount: Int) {
        Log.d("AppInterface", "requestRewardedAdForTask() called with reward: $rewardAmount")
        try {
            val currentId = sharedPrefs.getString("ff_player_id", "") ?: ""
            mainActivity.runOnUiThread {
                mainActivity.requestRewardedAdForTask(rewardAmount, currentId)
            }
        } catch (e: Exception) {
            Log.e("AppInterface", "Error in requestRewardedAdForTask: ${e.message}")
        }
    }

    // --- FUNCIÓN REEMPLAZADA ---
    @JavascriptInterface
    fun requestRewardedAdForSpins(spinsAmount: Int) {
        Log.d("AppInterface", "requestRewardedAdForSpins() called with amount: $spinsAmount")
        try {
            mainActivity.runOnUiThread {
                mainActivity.requestRewardedAdForSpins(spinsAmount)
            }
        } catch (e: Exception) {
            Log.e("AppInterface", "Error in requestRewardedAdForSpins: ${e.message}")
        }
    }

    private fun loadUser(playerId: String) {
        Log.d("AppInterface", "loadUser() called for playerId: $playerId")

        try {
            if (playerId.isBlank() || playerId.length < 5) {
                Log.w("AppInterface", "Invalid playerId for loadUser: $playerId")
                updateWebViewUserUI(null, 0, 0)
                return
            }

            currentUserData?.let {
                usersRef.child(it.playerId).removeEventListener(usersListener ?: return@let)
            }

            val userRef = usersRef.child(playerId)
            usersListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val user = snapshot.getValue(UserData::class.java)
                        if (user != null) {
                            currentUserData = user.copy(playerId = playerId)
                            updateWebViewUserUI(playerId, user.tickets, user.passes)
                        } else {
                            Log.d("AppInterface", "User not found for ID $playerId, creating...")
                            initializeUser(playerId)
                        }
                        checkPrivateMessage(playerId)
                    } catch (e: Exception) {
                        Log.e("AppInterface", "Error processing user data: ${e.message}")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w("AppInterface", "User listener cancelled: ${error.message}")
                }
            }
            userRef.addValueEventListener(usersListener!!)
        } catch (e: Exception) {
            Log.e("AppInterface", "Error in loadUser: ${e.message}")
        }
    }

    private fun setupFirebaseListeners() {
        Log.d("AppInterface", "Setting up Firebase listeners")

        try {
            usersRef.orderByChild("passes").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val users = mutableListOf<UserData>()
                        snapshot.children.forEach { child ->
                            val user = child.getValue(UserData::class.java)?.copy(playerId = child.key ?: "")
                            if (user != null && user.playerId.isNotBlank()) {
                                users.add(user)
                            }
                        }
                        users.sortWith(compareByDescending<UserData> { it.passes }.thenByDescending { it.tickets })
                        val usersJson = Gson().toJson(users).replace("'", "\\'")
                        CoroutineScope(Dispatchers.Main).launch {
                            webView.evaluateJavascript("window.updateLeaderboard('$usersJson');", null)
                            webView.evaluateJavascript("window.updateMiniLeaderboard('$usersJson');", null)
                        }
                    } catch (e: Exception) {
                        Log.e("AppInterface", "Error processing leaderboard: ${e.message}")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w("AppInterface", "Leaderboard listener cancelled: ${error.message}")
                }
            })

            winnersRef.orderByChild("timestamp").limitToLast(10).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val winners = mutableListOf<WinnerData>()
                        snapshot.children.forEach { child ->
                            val winner = child.getValue(WinnerData::class.java)
                            if (winner != null && winner.winnerId.isNotBlank()) {
                                winners.add(winner)
                            }
                        }
                        winners.sortByDescending { it.timestamp }
                        val winnersJson = Gson().toJson(winners).replace("'", "\\'")
                        CoroutineScope(Dispatchers.Main).launch {
                            webView.evaluateJavascript("window.updateWinners('$winnersJson');", null)
                        }
                    } catch (e: Exception) {
                        Log.e("AppInterface", "Error processing winners: ${e.message}")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w("AppInterface", "Winners listener cancelled: ${error.message}")
                }
            })
        } catch (e: Exception) {
            Log.e("AppInterface", "Error setting up Firebase listeners: ${e.message}")
        }
    }

    private fun checkPrivateMessage(playerId: String) {
        Log.d("AppInterface", "Checking private messages for: $playerId")

        try {
            if (playerId.isBlank()) return

            val privateMessageRef = database.getReference("privateMessages/$playerId")

            privateMessageListener?.let { privateMessageRef.removeEventListener(it) }

            privateMessageListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        try {
                            val messageData = snapshot.value as? Map<*, *>
                            messageData?.let { data ->
                                val type = data["type"] as? String
                                val message = data["message"] as? String
                                if (type != null && message != null) {
                                    val icon = when (type) {
                                        "win" -> "success"
                                        else -> "info"
                                    }
                                    val title = when (type) {
                                        "win" -> "¡FELICITACIONES, HAS GANADO!"
                                        else -> "Mensaje del Administrador"
                                    }

                                    val escapedMessage = message.replace("'", "\\'").replace("\n", "\\n")
                                    val escapedTitle = title.replace("'", "\\'")

                                    CoroutineScope(Dispatchers.Main).launch {
                                        val jsCode = """
                                            Swal.fire({
                                                icon: '$icon',
                                                title: '$escapedTitle',
                                                text: '$escapedMessage',
                                                confirmButtonText: 'Entendido'
                                            }).then(() => {
                                                if (window.Android) {
                                                    window.Android.clearPrivateMessage('$playerId');
                                                }
                                            });
                                        """.trimIndent()
                                        webView.evaluateJavascript(jsCode, null)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("AppInterface", "Error processing private message: ${e.message}")
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w("AppInterface", "Private message listener cancelled: ${error.message}")
                }
            }
            privateMessageRef.addListenerForSingleValueEvent(privateMessageListener!!)
        } catch (e: Exception) {
            Log.e("AppInterface", "Error in checkPrivateMessage: ${e.message}")
        }
    }

    @JavascriptInterface
    fun clearPrivateMessage(playerId: String) {
        Log.d("AppInterface", "clearPrivateMessage() called for ID: $playerId")
        if (playerId.isBlank()) return
        try {
            val messageRef = database.getReference("privateMessages/$playerId")
            messageRef.removeValue().addOnSuccessListener {
                Log.d("AppInterface", "Private message cleared for $playerId")
                privateMessageListener?.let {
                    messageRef.removeEventListener(it)
                    privateMessageListener = null
                }
            }
        } catch (e: Exception) {
            Log.e("AppInterface", "Exception in clearPrivateMessage: ${e.message}")
        }
    }

    private fun updateWebViewUserUI(playerId: String?, tickets: Long, passes: Long) {
        try {
            val playerDisplay = if (playerId.isNullOrEmpty()) "null" else "'$playerId'"
            CoroutineScope(Dispatchers.Main).launch {
                webView.evaluateJavascript("window.updateUserUI($playerDisplay, $tickets, $passes);", null)
            }
        } catch (e: Exception) {
            Log.e("AppInterface", "Error updating WebView UI: ${e.message}")
        }
    }

    fun cleanup() {
        Log.d("AppInterface", "Cleaning up WebAppInterface")

        try {
            // La llamada a destroyAllAdMobBanners() ha sido eliminada.

            currentUserData?.let {
                usersRef.child(it.playerId).removeEventListener(usersListener ?: return@let)
                database.getReference("privateMessages/${it.playerId}").removeEventListener(privateMessageListener ?: return@let)
            }

            usersListener = null
            privateMessageListener = null
            currentUserData = null

            Log.d("AppInterface", "WebAppInterface cleanup completed successfully")
        } catch (e: Exception) {
            Log.e("AppInterface", "Error during cleanup: ${e.message}")
        }
    }
}