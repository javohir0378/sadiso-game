package com.sadiso.game.tdlib

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.sadiso.game.BuildConfig
import java.util.Locale
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

class TdlibController(context: Context) {

    interface Listener {
        fun onAuthState(state: TdApi.AuthorizationState)
        fun onError(context: String, error: TdApi.Error)
    }

    var listener: Listener? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val databaseDirectory = context.filesDir.absolutePath + "/tdlib"
    private val filesDirectory = context.filesDir.absolutePath + "/tdlib-files"

    private val client: Client = Client.create(
        { obj -> handleUpdate(obj) },
        { e -> e.printStackTrace() },
        { e -> e.printStackTrace() }
    )

    private fun handleUpdate(obj: TdApi.Object) {
        if (obj is TdApi.UpdateAuthorizationState) {
            val state = obj.authorizationState
            if (state is TdApi.AuthorizationStateWaitTdlibParameters) {
                sendParameters()
            }
            mainHandler.post { listener?.onAuthState(state) }
        }
    }

    private fun sendParameters() {
        val params = TdApi.SetTdlibParameters().apply {
            databaseDirectory = this@TdlibController.databaseDirectory
            filesDirectory = this@TdlibController.filesDirectory
            useFileDatabase = true
            useChatInfoDatabase = true
            useMessageDatabase = true
            useSecretChats = false
            apiId = BuildConfig.TD_API_ID
            apiHash = BuildConfig.TD_API_HASH
            systemLanguageCode = Locale.getDefault().language.ifEmpty { "en" }
            deviceModel = Build.MODEL ?: "Android"
            systemVersion = Build.VERSION.RELEASE ?: ""
            applicationVersion = BuildConfig.VERSION_NAME
        }
        send(params, "setParameters")
    }

    private fun send(function: TdApi.Function<*>, context: String) {
        client.send(function) { obj ->
            if (obj is TdApi.Error) {
                mainHandler.post { listener?.onError(context, obj) }
            }
        }
    }

    fun sendPhoneNumber(phone: String) {
        send(TdApi.SetAuthenticationPhoneNumber(phone, null), "sendPhoneNumber")
    }

    fun sendCode(code: String) {
        send(TdApi.CheckAuthenticationCode(code), "sendCode")
    }

    fun sendPassword(password: String) {
        send(TdApi.CheckAuthenticationPassword(password), "sendPassword")
    }

    fun sendEmailAddress(email: String) {
        send(TdApi.SetAuthenticationEmailAddress(email), "sendEmailAddress")
    }

    fun sendEmailCode(code: String) {
        send(TdApi.CheckAuthenticationEmailCode(TdApi.EmailAddressAuthenticationCode(code)), "sendEmailCode")
    }

    fun sendRegistration(firstName: String, lastName: String) {
        send(TdApi.RegisterUser(firstName, lastName, false), "sendRegistration")
    }

    fun logOut() {
        send(TdApi.LogOut(), "logOut")
    }
}
