package com.follgramer.diamantesproplayers

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.google.firebase.database.*
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log
import com.google.firebase.functions.FirebaseFunctions

class WebAppInterface(private val context: Context, private val webView: ObservableWebView, private val mainActivity: MainActivity) {

    private val functions = FirebaseFunctions.getInstance()
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val usersRef: DatabaseReference = database.getReference("users")
    private val winnersRef: DatabaseReference = database.getReference("winners")
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("DiamantesProPlayersPrefs", Context.MODE_PRIVATE)

    private var currentUserData: UserData? = null
    private var usersListener: ValueEventListener? = null
    private var privateMessageListener: ValueEventListener? = null

    init {
        val savedPlayerId = sharedPrefs.getString("ff_player_id", null)
        if (savedPlayerId != null) {
            loadUser(savedPlayerId)
        }
        setupFirebaseListeners()
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
        val playerId = sharedPrefs.getString("ff_player_id", null)
        if (playerId != null) {
            currentUserData?.let {
                updateWebViewUserUI(it.playerId, it.tickets, it.passes)
            } ?: run {
                loadUser(playerId)
            }
        } else {
            updateWebViewUserUI(null, 0, 0)
        }
    }

    @JavascriptInterface
    fun getPlayerId(): String? {
        val playerId = sharedPrefs.getString("ff_player_id", null)
        Log.d("AppInterface", "getPlayerId() called from JavaScript. Returning: $playerId")
        return playerId
    }

    @JavascriptInterface
    fun savePlayerId(newPlayerId: String) {
        Log.d("AppInterface", "savePlayerId() called from JavaScript with ID: $newPlayerId")

        val editor = sharedPrefs.edit()
        editor.putString("ff_player_id", newPlayerId)
        editor.apply()

        initializeUser(newPlayerId)

        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, "ID de jugador guardado: $newPlayerId", Toast.LENGTH_SHORT).show()
        }

        Log.d("AppInterface", "Player ID saved and user initialized.")
    }

    @JavascriptInterface
    fun saveConsent(hasConsented: Boolean) {
        Log.d("AppInterface", "saveConsent() called with hasConsented: $hasConsented")
        val editor = sharedPrefs.edit()
        editor.putBoolean("adConsent", hasConsented)
        editor.apply()
    }

    @JavascriptInterface
    fun hasConsented(): Boolean {
        val consented = sharedPrefs.getBoolean("adConsent", false)
        Log.d("AppInterface", "hasConsented() called, returning: $consented")
        return consented
    }

    @JavascriptInterface
    fun openAdSettings() {
        Log.d("AppInterface", "openAdSettings() called")
        val intent = Intent("com.google.android.gms.ads.identifier.service.ADVERTISING_ID_SETTINGS")
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("AppInterface", "Error opening ad settings: ${e.message}")
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(context, "No se pudo abrir la configuración de anuncios.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @JavascriptInterface
    fun loadBannerAd() {
        mainActivity.runOnUiThread {
            mainActivity.loadBannerAd()
        }
    }

    private fun initializeUser(playerId: String) {
        Log.d("AppInterface", "Initializing user: $playerId")

        val data = hashMapOf(
            "playerId" to playerId
        )

        functions
            .getHttpsCallable("initializeUser")
            .call(data)
            .addOnSuccessListener { result ->
                Log.d("AppInterface", "User initialized successfully: ${result.data}")
                loadUser(playerId)
            }
            .addOnFailureListener { e ->
                Log.e("AppInterface", "Error initializing user: ${e.message}", e)
                loadUser(playerId)
            }
    }

    @JavascriptInterface
    fun sendTicketsToId(targetPlayerId: String, amount: Int) {
        Log.d("AppInterface", "sendTicketsToId() called from JavaScript for ID: $targetPlayerId with amount: $amount")

        if (targetPlayerId.isEmpty()) {
            CoroutineScope(Dispatchers.Main).launch {
                webView.evaluateJavascript("Swal.fire('Error', 'ID de jugador destino no válida.', 'warning');", null)
            }
            return
        }

        val data = hashMapOf(
            "playerId" to targetPlayerId,
            "amount" to amount
        )

        functions
            .getHttpsCallable("sendTicketsToId")
            .call(data)
            .addOnSuccessListener { httpsCallableResult ->
                Log.d("AppInterface", "sendTicketsToId Cloud Function llamada con éxito: ${httpsCallableResult.data}")

                val updatedUserResult = httpsCallableResult.data as? Map<*, *>
                val userMap = updatedUserResult?.get("user") as? Map<*, *>

                if (userMap != null && userMap["playerId"] == sharedPrefs.getString("ff_player_id", null)) {
                    val tickets = (userMap["tickets"] as? Number)?.toLong() ?: 0L
                    val passes = (userMap["passes"] as? Number)?.toLong() ?: 0L
                    val playerId = userMap["playerId"] as? String ?: ""
                    updateWebViewUserUI(playerId, tickets, passes)
                }

                CoroutineScope(Dispatchers.Main).launch {
                    webView.evaluateJavascript("Swal.fire('Éxito', 'Tickets enviados correctamente.', 'success');", null)
                }
            }
            .addOnFailureListener { e ->
                Log.e("AppInterface", "Error llamando a sendTicketsToId Cloud Function: ${e.message}", e)
                CoroutineScope(Dispatchers.Main).launch {
                    webView.evaluateJavascript("Swal.fire('Error', 'Error al enviar tickets. Verifica que las Cloud Functions estén activas.', 'error');", null)
                }
            }
    }

    @JavascriptInterface
    fun addTickets(amount: Int) {
        Log.d("AppInterface", "addTickets() called from JavaScript with amount: $amount")
        val playerId = sharedPrefs.getString("ff_player_id", null)
        if (playerId.isNullOrEmpty()) {
            CoroutineScope(Dispatchers.Main).launch {
                webView.evaluateJavascript("Swal.fire('Error', 'Configura tu ID de jugador primero para ganar tickets.', 'warning');", null)
            }
            return
        }

        val data = hashMapOf(
            "playerId" to playerId,
            "amount" to amount
        )

        functions
            .getHttpsCallable("addTickets")
            .call(data)
            .addOnSuccessListener { httpsCallableResult ->
                Log.d("AppInterface", "addTickets Cloud Function llamada con éxito: ${httpsCallableResult.data}")

                val updatedUserResult = httpsCallableResult.data as? Map<*, *>
                val userMap = updatedUserResult?.get("user") as? Map<*, *>

                if (userMap != null) {
                    val tickets = (userMap["tickets"] as? Number)?.toLong() ?: 0L
                    val passes = (userMap["passes"] as? Number)?.toLong() ?: 0L
                    val playerId = userMap["playerId"] as? String ?: ""
                    updateWebViewUserUI(playerId, tickets, passes)

                    val cfMessage = updatedUserResult["message"] as? String
                    if (cfMessage != null && cfMessage.contains("pase")) {
                        CoroutineScope(Dispatchers.Main).launch {
                            webView.evaluateJavascript(
                                """
                                Swal.fire({
                                    title: '🎉 ¡PASE GANADO!',
                                    html: '<p style="font-size: 1.2em;">¡Felicidades, has ganado un pase!</p><p style="color: #51cf66;">Contador reiniciado a ${tickets} tickets</p>',
                                    icon: 'success',
                                    timer: 3000,
                                    showConfirmButton: false
                                });
                                """.trimIndent(), null
                            )
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("AppInterface", "Error llamando a addTickets Cloud Function: ${e.message}", e)
                CoroutineScope(Dispatchers.Main).launch {
                    when {
                        e.message?.contains("NOT_FOUND") == true -> {
                            webView.evaluateJavascript("Swal.fire('Error', 'Las Cloud Functions no están disponibles. Contacta al administrador.', 'error');", null)
                        }
                        else -> {
                            webView.evaluateJavascript("Swal.fire('Error', 'Error al guardar tickets: ${e.message}', 'error');", null)
                        }
                    }
                }
            }
    }

    @JavascriptInterface
    fun requestRewardedAdForTask(rewardAmount: Int) {
        Log.d("AppInterface", "requestRewardedAdForTask() called with reward: $rewardAmount")
        val currentId = sharedPrefs.getString("ff_player_id", "") ?: ""
        mainActivity.runOnUiThread {
            mainActivity.requestRewardedAdForTask(rewardAmount, currentId)
        }
    }

    @JavascriptInterface
    fun requestRewardedAdForSpins(spinsAmount: Int) {
        Log.d("AppInterface", "requestRewardedAdForSpins() called with amount: $spinsAmount spins")
        mainActivity.runOnUiThread {
            mainActivity.requestRewardedAdForSpins(spinsAmount)
        }
    }

    private fun loadUser(playerId: String) {
        Log.d("AppInterface", "loadUser() called for playerId: $playerId")

        usersListener?.let { currentListener ->
            currentUserData?.playerId?.let { oldPlayerId ->
                usersRef.child(oldPlayerId).removeEventListener(currentListener)
                Log.d("AppInterface", "Removed old user listener for $oldPlayerId.")
            }
        }

        val userRef = usersRef.child(playerId)
        usersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(UserData::class.java)
                if (user != null) {
                    currentUserData = user.copy(playerId = playerId)
                    Log.d("AppInterface", "User data loaded from Firebase for $playerId: ${user.tickets} tickets, ${user.passes} passes")
                    updateWebViewUserUI(playerId, user.tickets, user.passes)
                } else {
                    Log.d("AppInterface", "User $playerId not found in Firebase, creating new user...")
                    addTickets(0)
                }
                checkPrivateMessage(playerId)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AppInterface", "Firebase listener cancelled for user: ${error.message}")
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Error cargando usuario: ${error.message}", Toast.LENGTH_LONG).show()
                    updateWebViewUserUI(null, 0, 0)
                }
            }
        }
        userRef.addValueEventListener(usersListener!!)
    }

    private fun setupFirebaseListeners() {
        Log.d("AppInterface", "Setting up Firebase listeners for Leaderboard and Winners.")
        usersRef.orderByChild("passes").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val users = mutableListOf<UserData>()
                snapshot.children.forEach { child ->
                    val user = child.getValue(UserData::class.java)?.copy(playerId = child.key ?: "")
                    if (user != null) {
                        users.add(user)
                    }
                }
                users.sortWith(compareByDescending<UserData> { it.passes }.thenByDescending { it.tickets })
                val usersJson = Gson().toJson(users).replace("'", "\\'")
                CoroutineScope(Dispatchers.Main).launch {
                    webView.evaluateJavascript("window.updateLeaderboard('$usersJson');", null)
                    webView.evaluateJavascript("window.updateMiniLeaderboard('$usersJson');", null)
                    Log.d("AppInterface", "Leaderboard data updated in WebView.")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AppInterface", "Firebase leaderboard listener cancelled: ${error.message}")
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Error cargando clasificación: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })

        winnersRef.orderByChild("timestamp").limitToLast(10).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val winners = mutableListOf<WinnerData>()
                snapshot.children.forEach { child ->
                    val winner = child.getValue(WinnerData::class.java)
                    if (winner != null) {
                        winners.add(winner)
                    }
                }
                winners.sortByDescending { it.timestamp }
                val winnersJson = Gson().toJson(winners).replace("'", "\\'")
                CoroutineScope(Dispatchers.Main).launch {
                    webView.evaluateJavascript("window.updateWinners('$winnersJson');", null)
                    Log.d("AppInterface", "Winners data updated in WebView.")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AppInterface", "Firebase winners listener cancelled: ${error.message}")
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Error cargando ganadores: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    @JavascriptInterface
    fun clearPrivateMessage(playerId: String) {
        Log.d("AppInterface", "clearPrivateMessage() called from JavaScript for ID: $playerId")
        val messageRef = database.getReference("privateMessages/$playerId")
        messageRef.removeValue()
            .addOnSuccessListener {
                Log.d("AppInterface", "Private message for $playerId cleared from Firebase.")
                privateMessageListener?.let {
                    database.getReference("privateMessages/$playerId").removeEventListener(it)
                    privateMessageListener = null
                    Log.d("AppInterface", "Private message listener for $playerId removed after clearing.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("AppInterface", "Error clearing private message for $playerId: ${e.message}")
            }
    }

    private fun checkPrivateMessage(playerId: String) {
        Log.d("AppInterface", "Checking private messages for playerId: $playerId")
        val privateMessageRef = database.getReference("privateMessages/$playerId")

        privateMessageListener?.let { currentListener ->
            privateMessageRef.removeEventListener(currentListener)
            Log.d("AppInterface", "Removed old private message listener for $playerId.")
        }

        privateMessageListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val messageData = snapshot.value as? Map<*, *>
                    messageData?.let { data ->
                        val type = data["type"] as? String
                        val message = data["message"] as? String
                        if (type != null && message != null) {
                            val icon = when (type) {
                                "win" -> "success"
                                "loss" -> "info"
                                else -> "info"
                            }
                            val title = when (type) {
                                "win" -> "⭐ ¡FELICITACIONES, HAS GANADO! ⭐"
                                "loss" -> "🎉 ¡El Sorteo ha Terminado! 🎉"
                                else -> "Mensaje del Administrador"
                            }

                            val escapedMessage = message.replace("\"", "\\\"").replace("\n", "\\n")
                            val escapedTitle = title.replace("\"", "\\\"").replace("\n", "\\n")

                            CoroutineScope(Dispatchers.Main).launch {
                                val jsCode = """
                                    Swal.fire({
                                        icon: '$icon',
                                        title: '$escapedTitle',
                                        text: '$escapedMessage',
                                        confirmButtonText: '${if (type == "win") "¡Genial!" else "Entendido"}'
                                    }).then(() => {
                                        if (window.Android && window.Android.clearPrivateMessage) {
                                            window.Android.clearPrivateMessage('$playerId');
                                        }
                                    });
                                """.trimIndent()
                                webView.evaluateJavascript(jsCode, null)
                                Log.d("AppInterface", "Private message modal evaluated for $playerId.")
                            }
                        }
                    }
                } else {
                    Log.d("AppInterface", "No private message found for $playerId.")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("AppInterface", "Firebase private message listener cancelled for $playerId: ${error.message}")
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Error al verificar mensajes: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        privateMessageRef.addValueEventListener(privateMessageListener!!)
    }

    private fun updateWebViewUserUI(playerId: String?, tickets: Long, passes: Long) {
        val playerDisplay = if (playerId.isNullOrEmpty()) "null" else "'$playerId'"
        CoroutineScope(Dispatchers.Main).launch {
            webView.evaluateJavascript("window.updateUserUI($playerDisplay, $tickets, $passes);", null)
            Log.d("AppInterface", "updateUserUI() evaluated for playerId: $playerId, tickets: $tickets, passes: $passes")
        }
    }
}