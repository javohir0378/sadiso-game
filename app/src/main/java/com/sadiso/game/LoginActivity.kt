package com.sadiso.game

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private var currentErrorView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val root = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.bg_gradient)
        }
        root.addView(LoginBackdropView(this), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        val loginRoot = FrameLayout(this)
        root.addView(loginRoot, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        stepContainer = FrameLayout(this).apply {
            setPadding(dp(28), 0, dp(28), 0)
        }
        loginRoot.addView(
            stepContainer,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
        )

        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(loginRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        showLoadingStep()

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
        showFieldError(error.message ?: "Xatolik yuz berdi")
    }

    // ---- shared building blocks ----

    private fun newCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundResource(R.drawable.card_bg)
        val pad = dp(28)
        setPadding(pad, pad, pad, pad)
    }

    private fun LinearLayout.addChild(view: View, topMargin: Int = 0, matchWidth: Boolean = false, heightDp: Int? = null) {
        val w = if (matchWidth) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT
        val h = heightDp?.let { dp(it) } ?: LinearLayout.LayoutParams.WRAP_CONTENT
        val lp = LinearLayout.LayoutParams(w, h)
        lp.topMargin = topMargin
        if (!matchWidth) lp.gravity = Gravity.CENTER_HORIZONTAL
        addView(view, lp)
    }

    private fun titleText(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(ContextCompat.getColor(context, R.color.accent_gold))
        textSize = 20f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
    }

    private fun subtitleText(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(ContextCompat.getColor(context, R.color.label_muted))
        textSize = 13f
        gravity = Gravity.CENTER
    }

    private fun linkText(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(ContextCompat.getColor(context, R.color.label_muted))
        textSize = 13f
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
    }

    private fun errorText(): TextView = TextView(this).apply {
        setTextColor(ContextCompat.getColor(context, R.color.error_red))
        textSize = 12f
        visibility = View.GONE
    }

    private fun inputField(hint: String, inputType: Int, centered: Boolean = false): EditText = EditText(this).apply {
        this.hint = hint
        setHintTextColor(ContextCompat.getColor(context, R.color.label_muted))
        setTextColor(ContextCompat.getColor(context, R.color.text_title))
        this.inputType = inputType
        setBackgroundResource(R.drawable.input_bg)
        val padH = dp(16)
        val padV = dp(14)
        setPadding(padH, padV, padH, padV)
        if (centered) {
            gravity = Gravity.CENTER
            letterSpacing = 0.3f
        }
    }

    private fun primaryButton(text: String): Button = android.widget.Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.parseColor("#2A1B04"))
        setTypeface(typeface, Typeface.BOLD)
        setBackgroundResource(R.drawable.primary_button_bg)
    }

    // ---- error / shake ----

    private fun showFieldError(message: String) {
        val err = currentErrorView ?: return
        err.text = message
        err.visibility = View.VISIBLE
        currentStepView?.let { shake(it) }
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

    // ---- steps ----

    private fun showLoadingStep() = showStep("loading") {
        currentErrorView = null
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.card_bg)
            val pad = dp(36)
            setPadding(pad, pad, pad, pad)

            addChild(ProgressBar(this@LoginActivity).apply {
                indeterminateTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.accent_gold)
                )
            }, heightDp = 40)

            addChild(TextView(this@LoginActivity).apply {
                text = getString(R.string.login_loading)
                setTextColor(ContextCompat.getColor(context, R.color.text_title))
                textSize = 15f
            }, topMargin = dp(16))
        }
    }

    private fun showPhoneStep() = showStep("phone") {
        val card = newCard()
        card.addChild(titleText(getString(R.string.login_phone_title)))
        card.addChild(subtitleText(getString(R.string.login_phone_subtitle)), topMargin = dp(6))

        val input = inputField(getString(R.string.login_phone_hint), InputType.TYPE_CLASS_PHONE)
        card.addChild(input, topMargin = dp(22), matchWidth = true)

        val error = errorText()
        currentErrorView = error
        card.addChild(error, topMargin = dp(8), matchWidth = true)

        val button = primaryButton(getString(R.string.login_continue))
        card.addChild(button, topMargin = dp(22), matchWidth = true, heightDp = 52)

        button.setOnClickListener {
            val phone = input.text.toString().trim().replace(" ", "")
            if (phone.length < 6) {
                showFieldError("Raqamni to'liq kiriting")
                return@setOnClickListener
            }
            controller.sendPhoneNumber(phone)
            showLoadingStep()
        }
        card
    }

    private fun showCodeStep() = showStep("code") {
        val card = newCard()
        card.addChild(titleText(getString(R.string.login_code_title)))
        card.addChild(subtitleText(getString(R.string.login_code_subtitle)), topMargin = dp(6))

        val input = inputField(getString(R.string.login_code_hint), InputType.TYPE_CLASS_NUMBER, centered = true)
        card.addChild(input, topMargin = dp(22), matchWidth = true)

        val error = errorText()
        currentErrorView = error
        card.addChild(error, topMargin = dp(8), matchWidth = true)

        val button = primaryButton(getString(R.string.login_continue))
        card.addChild(button, topMargin = dp(22), matchWidth = true, heightDp = 52)

        val back = linkText(getString(R.string.login_code_back))
        card.addChild(back, topMargin = dp(16))

        button.setOnClickListener {
            val code = input.text.toString().trim()
            if (code.isEmpty()) {
                showFieldError("Kodni kiriting")
                return@setOnClickListener
            }
            controller.sendCode(code)
        }
        back.setOnClickListener {
            controller.logOut()
            showPhoneStep()
        }
        card
    }

    private fun showPasswordStep(state: TdApi.AuthorizationStateWaitPassword) = showStep("password") {
        val card = newCard()
        card.addChild(titleText(getString(R.string.login_password_title)))
        val hint = state.passwordHint
        val subtitleStr = if (!hint.isNullOrEmpty()) {
            getString(R.string.login_password_subtitle) + " ($hint)"
        } else {
            getString(R.string.login_password_subtitle)
        }
        card.addChild(subtitleText(subtitleStr), topMargin = dp(6))

        val input = inputField(
            getString(R.string.login_password_hint),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        card.addChild(input, topMargin = dp(22), matchWidth = true)

        val error = errorText()
        currentErrorView = error
        card.addChild(error, topMargin = dp(8), matchWidth = true)

        val button = primaryButton(getString(R.string.login_continue))
        card.addChild(button, topMargin = dp(22), matchWidth = true, heightDp = 52)

        button.setOnClickListener {
            val password = input.text.toString()
            if (password.isEmpty()) {
                showFieldError("Parolni kiriting")
                return@setOnClickListener
            }
            controller.sendPassword(password)
        }
        card
    }

    private fun showEmailStep() = showStep("email") {
        val card = newCard()
        card.addChild(titleText(getString(R.string.login_email_title)))
        card.addChild(subtitleText(getString(R.string.login_email_subtitle)), topMargin = dp(6))

        val input = inputField(
            getString(R.string.login_email_hint),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        card.addChild(input, topMargin = dp(22), matchWidth = true)

        val error = errorText()
        currentErrorView = error
        card.addChild(error, topMargin = dp(8), matchWidth = true)

        val button = primaryButton(getString(R.string.login_continue))
        card.addChild(button, topMargin = dp(22), matchWidth = true, heightDp = 52)

        button.setOnClickListener {
            val email = input.text.toString().trim()
            if (!email.contains("@")) {
                showFieldError("To'g'ri email kiriting")
                return@setOnClickListener
            }
            controller.sendEmailAddress(email)
        }
        card
    }

    private fun showEmailCodeStep() = showStep("email_code") {
        val card = newCard()
        card.addChild(titleText(getString(R.string.login_email_code_title)))
        card.addChild(subtitleText(getString(R.string.login_email_code_subtitle)), topMargin = dp(6))

        val input = inputField(getString(R.string.login_email_code_hint), InputType.TYPE_CLASS_NUMBER, centered = true)
        card.addChild(input, topMargin = dp(22), matchWidth = true)

        val error = errorText()
        currentErrorView = error
        card.addChild(error, topMargin = dp(8), matchWidth = true)

        val button = primaryButton(getString(R.string.login_continue))
        card.addChild(button, topMargin = dp(22), matchWidth = true, heightDp = 52)

        button.setOnClickListener {
            val code = input.text.toString().trim()
            if (code.isEmpty()) {
                showFieldError("Kodni kiriting")
                return@setOnClickListener
            }
            controller.sendEmailCode(code)
        }
        card
    }

    private fun showRegistrationStep() = showStep("registration") {
        val card = newCard()
        card.addChild(titleText(getString(R.string.login_registration_title)))
        card.addChild(subtitleText(getString(R.string.login_registration_subtitle)), topMargin = dp(6))

        val first = inputField(
            getString(R.string.login_first_name_hint),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME
        )
        card.addChild(first, topMargin = dp(22), matchWidth = true)

        val last = inputField(
            getString(R.string.login_last_name_hint),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME
        )
        card.addChild(last, topMargin = dp(14), matchWidth = true)

        val error = errorText()
        currentErrorView = error
        card.addChild(error, topMargin = dp(8), matchWidth = true)

        val button = primaryButton(getString(R.string.login_continue))
        card.addChild(button, topMargin = dp(22), matchWidth = true, heightDp = 52)

        button.setOnClickListener {
            val f = first.text.toString().trim()
            if (f.isEmpty()) {
                showFieldError("Ismingizni kiriting")
                return@setOnClickListener
            }
            controller.sendRegistration(f, last.text.toString().trim())
        }
        card
    }

    // ---- transition ----

    private fun showStep(tag: String, builder: () -> View) {
        if (currentStepTag == tag) return
        currentStepTag = tag

        val newView = builder()
        val oldView = currentStepView
        currentStepView = newView

        newView.alpha = 0f
        newView.translationX = 60f
        stepContainer.addView(
            newView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        )

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
