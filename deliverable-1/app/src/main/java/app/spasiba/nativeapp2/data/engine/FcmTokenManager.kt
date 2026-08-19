package app.spasiba.nativeapp2.data.engine

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

class FcmTokenManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("eunoia_prefs", Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient()

    private val _deviceId = MutableStateFlow<String>("")
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    private val _fcmToken = MutableStateFlow<String?>(null)
    val fcmToken: StateFlow<String?> = _fcmToken.asStateFlow()

    val mobileNumber = MutableStateFlow<String>(prefs.getString("mobile_number", null) ?: "")
    val isMobileRequired = MutableStateFlow<Boolean>(false)

    val isDeviceBlocked = MutableStateFlow<Boolean>(false)
    val blockReason = MutableStateFlow<String>("Account or Device Restricted by Administrator")
    val isEunoiaDisabled = MutableStateFlow<Boolean>(false)

    companion object {
        const val API_BASE_URL = "https://spasiba.vercel.app"

        @Volatile
        private var INSTANCE: FcmTokenManager? = null

        fun getInstance(context: Context): FcmTokenManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FcmTokenManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        var id = prefs.getString("device_id", null)
        if (id.isNullOrBlank()) {
            val androidId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (e: Exception) { null }

            id = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                "SPASIBA-${androidId.takeLast(6).uppercase()}"
            } else {
                "SPASIBA-${UUID.randomUUID().toString().take(6).uppercase()}"
            }
            prefs.edit().putString("device_id", id).apply()
        }
        _deviceId.value = id ?: "SPASIBA-000000"

        isDeviceBlocked.value = prefs.getBoolean("is_device_blocked", false)
        blockReason.value = prefs.getString("block_reason", "Account or Device Restricted by Administrator") ?: "Account or Device Restricted"
        isEunoiaDisabled.value = prefs.getBoolean("is_eunoia_disabled", false)

        // Instant auto-detect SIM or hardware mobile tag
        val detectedMobile = tryAutoReadSimPhoneNumber()
        mobileNumber.value = detectedMobile
        prefs.edit().putString("mobile_number", detectedMobile).apply()

        // Immediate background upload to DB on init
        registerTokenWithBackend(null, detectedMobile)
    }

    fun tryAutoReadSimPhoneNumber(): String {
        try {
            val savedMobile = prefs.getString("mobile_number", null)
            if (!savedMobile.isNullOrBlank() && !savedMobile.startsWith("SIM-DEV-")) {
                return savedMobile
            }

            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val line1 = try { telephonyManager?.line1Number } catch (e: Exception) { null }
            if (!line1.isNullOrBlank()) {
                return line1
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val activeSubs = try { subManager?.activeSubscriptionInfoList } catch (e: Exception) { null }
                if (!activeSubs.isNullOrEmpty()) {
                    for (sub in activeSubs) {
                        val num = sub.number
                        if (!num.isNullOrBlank()) {
                            return num
                        }
                    }
                    val firstSub = activeSubs.first()
                    val carrierName = firstSub.carrierName?.toString() ?: firstSub.displayName?.toString() ?: "SIM"
                    val simSlot = firstSub.simSlotIndex
                    val subscriptionId = firstSub.subscriptionId
                    return "$carrierName (Slot $simSlot / Sub $subscriptionId)"
                }
            }

            val simOperator = telephonyManager?.simOperatorName
            val networkOperator = telephonyManager?.networkOperatorName
            if (!simOperator.isNullOrBlank()) {
                return "SIM ($simOperator)"
            } else if (!networkOperator.isNullOrBlank()) {
                return "SIM ($networkOperator)"
            }
        } catch (e: Exception) {
            Log.e("FcmTokenManager", "Error reading SIM card info: ${e.message}")
        }

        // Guaranteed hardware fallback ID so DB is NEVER null
        val shortDeviceId = _deviceId.value.takeLast(6)
        return "SIM-DEV-$shortDeviceId"
    }

    private fun isFirebaseAvailable(): Boolean {
        return runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
    }

    fun fetchAndRegisterFcmToken() {
        initialize(scope)
    }

    fun submitMobileNumber(number: String) {
        val cleanNumber = number.trim()
        if (cleanNumber.isNotBlank()) {
            mobileNumber.value = cleanNumber
            prefs.edit().putString("mobile_number", cleanNumber).apply()
            registerTokenWithBackend(_fcmToken.value, cleanNumber)
        }
    }

    fun initialize(coroutineScope: CoroutineScope) {
        scope.launch {
            try {
                val currentMobile = tryAutoReadSimPhoneNumber()
                mobileNumber.value = currentMobile
                prefs.edit().putString("mobile_number", currentMobile).apply()

                if (isFirebaseAvailable()) {
                    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val token = task.result
                            _fcmToken.value = token
                            Log.d("FcmTokenManager", "FCM token retrieved successfully: ${token?.take(15)}...")
                            registerTokenWithBackend(token, currentMobile)
                        } else {
                            Log.w("FcmTokenManager", "Fetching FCM registration token failed: ${task.exception?.message}")
                            registerTokenWithBackend(null, currentMobile)
                        }
                    }
                } else {
                    Log.d("FcmTokenManager", "FirebaseApp not initialized. Skipping push token registration. Relying on DB polling.")
                    registerTokenWithBackend(null, currentMobile)
                }
            } catch (e: Exception) {
                Log.e("FcmTokenManager", "Error in FCM token initialization: ${e.message}.")
                registerTokenWithBackend(null, mobileNumber.value)
            }
        }
    }

    fun registerTokenWithBackend(token: String?, customMobile: String? = null) {
        scope.launch {
            try {
                val targetMobile = customMobile ?: mobileNumber.value.ifBlank { tryAutoReadSimPhoneNumber() }
                val jsonPayload = JSONObject().apply {
                    put("deviceId", _deviceId.value)
                    put("userId", _deviceId.value)
                    put("mobileNumber", targetMobile)
                    if (!token.isNullOrBlank()) {
                        put("fcmToken", token)
                    } else {
                        put("fcmToken", JSONObject.NULL)
                    }
                    put("appVersion", "2.0.0-NATIVE")
                }.toString()

                val body = jsonPayload.toRequestBody("application/json".toMediaType())
                
                val urlsToTry = listOf(
                    "$API_BASE_URL/api/devices/register"
                )

                for (targetUrl in urlsToTry) {
                    try {
                        val request = Request.Builder()
                            .url(targetUrl)
                            .post(body)
                            .build()

                        httpClient.newCall(request).execute().use { response ->
                            Log.d("FcmTokenManager", "Device register response from $targetUrl: ${response.code}")
                            if (response.isSuccessful) {
                                val resStr = response.body?.string() ?: ""
                                if (resStr.isNotBlank()) {
                                    val json = JSONObject(resStr)
                                    val blocked = json.optBoolean("isBlocked", false)
                                    val reason = json.optString("blockReason", "Account or Device Restricted")
                                    val eunoiaDis = json.optBoolean("eunoiaDisabled", false)

                                    isDeviceBlocked.value = blocked
                                    blockReason.value = reason
                                    isEunoiaDisabled.value = eunoiaDis

                                    prefs.edit()
                                        .putBoolean("is_device_blocked", blocked)
                                        .putString("block_reason", reason)
                                        .putBoolean("is_eunoia_disabled", eunoiaDis)
                                        .putString("mobile_number", targetMobile)
                                        .apply()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("FcmTokenManager", "Could not post to $targetUrl: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("FcmTokenManager", "Failed to register device with backend: ${e.message}")
            }
        }
    }
}
