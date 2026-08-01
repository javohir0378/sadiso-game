package com.sadiso.game

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.sadiso.game.tdlib.TdlibController
import org.drinkless.tdlib.TdApi
import kotlin.math.sin

class LoginActivity : AppCompatActivity(), TdlibController.Listener {

    private lateinit var stepContainer: FrameLayout
    private lateinit var controller: TdlibController
    private var currentStepTag: String? = null
    private var currentStepView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        setContentView(R.layout.activity_login)

        val loginRoot = findViewById<View>(R.id.loginRoot)
        ViewCompat.setOnApplyWindowInsetsListener(loginRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        stepContainer = findViewById(R.id.stepContainer)
        showStep("loading", R.layout.step_loading) {}

        controller = TdlibController(applicationContext)
        controller.listener = this
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.listener = null
    }

    override fun onAuthState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> showPhoneStep()
            is TdApi.AuthorizationStateWaitCode -> showCodeStep()
            is TdApi.AuthorizationStateWaitPassword -> showPasswordStep(state)
            is TdApi.AuthorizationStateWaitEmailAddress -> showEmailStep()
            is TdApi.AuthorizationStateWaitEmailCode -> showEmailCodeStep()
            is TdApi.AuthorizationStateWaitRegistration -> showRegistrationStep()
            is TdApi.AuthorizationStateReady -> {
                startActivity(Intent(this, MainMenuActivity::class.java))
                finish()
            }
            else -> showLoadingStep()
        }
    }

    override fun onError(context: String, error: TdApi.Error) {
        val message = error.message ?: "Xatolik yuz berdi"
        when (context) {
            "sendPhoneNumber" -> showFieldError(R.id.phoneError, message)
            "sendCode" -> showFieldError(R.id.codeError, message)
            "sendPassword" -> showFieldError(R.id.passwordError, message)
            "sendEmailAddress" -> showFieldError(R.id.emailError, message)
            "sendEmailCode" -> showFieldError(R.id.emailCodeError, message)
            "sendRegistration" -> showFieldError(R.id.registrationError, message)
        }
    }

    private fun showFieldError(errorId: Int, message: String) {
        val stepView = currentStepView ?: return
        val errorView = stepView.findViewById<TextView>(errorId) ?: return
        errorView.text = message
        errorView.visibility = View.VISIBLE
        shake(stepView)
    }

    private fun shake(view: View) {
        val start = System.currentTimeMillis()
        val duration = 420L
        val amp = 14f
        val runnable = object : Runnable {
            override fun run() {
                val raw = ((System.currentTimeMillis() - start).toFloat() / duration).coerceIn(0f, 1f)
                if (raw < 1f) {
                    view.translationX = sin(raw * Math.PI.toFloat() * 6f) * amp * (1f - raw)
                    view.postOnAnimation(this)
                } else {
                    view.translationX = 0f
                }
            }
        }
        view.post(runnable)
    }

    private fun showLoadingStep() = showStep("loading", R.layout.step_loading) {}

    private fun showPhoneStep() = showStep("phone", R.layout.step_phone) { v ->
        val input = v.findViewById<EditText>(R.id.phoneInput)
        v.findViewById<Button>(R.id.phoneSubmit).setOnClickListener {
            val phone = input.text.toString().trim().replace(" ", "")
            if (phone.length < 6) {
                showFieldError(R.id.phoneError, "Raqamni to'liq kiriting")
                return@setOnClickListener
            }
            controller.sendPhoneNumber(phone)
            showLoadingStep()
        }
    }

    private fun showCodeStep() = showStep("code", R.layout.step_code) { v ->
        val input = v.findViewById<EditText>(R.id.codeInput)
        v.findViewById<Button>(R.id.codeSubmit).setOnClickListener {
            val code = input.text.toString().trim()
            if (code.isEmpty()) {
                showFieldError(R.id.codeError, "Kodni kiriting")
                return@setOnClickListener
            }
            controller.sendCode(code)
        }
        v.findViewById<TextView>(R.id.codeBack).setOnClickListener {
            controller.logOut()
            showPhoneStep()
        }
    }

    private fun showPasswordStep(state: TdApi.AuthorizationStateWaitPassword) =
        showStep("password", R.layout.step_password) { v ->
            val hint = state.passwordHint
            if (!hint.isNullOrEmpty()) {
                v.findViewById<TextView>(R.id.passwordSubtitle).text =
                    getString(R.string.login_password_subtitle) + " ($hint)"
            }
            val input = v.findViewById<EditText>(R.id.passwordInput)
            v.findViewById<Button>(R.id.passwordSubmit).setOnClickListener {
                val password = input.text.toString()
                if (password.isEmpty()) {
                    showFieldError(R.id.passwordError, "Parolni kiriting")
                    return@setOnClickListener
                }
                controller.sendPassword(password)
            }
        }

    private fun showEmailStep() = showStep("email", R.layout.step_email) { v ->
        val input = v.findViewById<EditText>(R.id.emailInput)
        v.findViewById<Button>(R.id.emailSubmit).setOnClickListener {
            val email = input.text.toString().trim()
            if (!email.contains("@")) {
                showFieldError(R.id.emailError, "To'g'ri email kiriting")
                return@setOnClickListener
            }
            controller.sendEmailAddress(email)
        }
    }

    private fun showEmailCodeStep() = showStep("email_code", R.layout.step_email_code) { v ->
        val input = v.findViewById<EditText>(R.id.emailCodeInput)
        v.findViewById<Button>(R.id.emailCodeSubmit).setOnClickListener {
            val code = input.text.toString().trim()
            if (code.isEmpty()) {
                showFieldError(R.id.emailCodeError, "Kodni kiriting")
                return@setOnClickListener
            }
            controller.sendEmailCode(code)
        }
    }

    private fun showRegistrationStep() = showStep("registration", R.layout.step_registration) { v ->
        val first = v.findViewById<EditText>(R.id.firstNameInput)
        val last = v.findViewById<EditText>(R.id.lastNameInput)
        v.findViewById<Button>(R.id.registrationSubmit).setOnClickListener {
            val f = first.text.toString().trim()
            if (f.isEmpty()) {
                showFieldError(R.id.registrationError, "Ismingizni kiriting")
                return@setOnClickListener
            }
            controller.sendRegistration(f, last.text.toString().trim())
        }
    }

    private fun showStep(tag: String, layoutRes: Int, bind: (View) -> Unit) {
        if (currentStepTag == tag) return
        currentStepTag = tag

        val newView = LayoutInflater.from(this).inflate(layoutRes, stepContainer, false)
        bind(newView)

        val oldView = currentStepView
        currentStepView = newView

        newView.alpha = 0f
        newView.translationX = 60f
        stepContainer.addView(newView)

        newView.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(320)
            .setInterpolator(OvershootInterpolator(0.8f))
            .start()

        if (oldView != null) {
            oldView.animate()
                .alpha(0f)
                .translationX(-60f)
                .setDuration(260)
                .withEndAction { stepContainer.removeView(oldView) }
                .start()
        }
    }
}
