package origon.example.android.ui.common

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import origon.example.android.R

/**
 * Single-line input for the endpoint screen. Wraps a styled EditText and
 * handles the error UX in one place: when [setError] is given a non-null
 * message the field shakes, shows a red border + inline caption, and
 * fires an error haptic. Mirrors the iOS OrigonInput.
 */
class OrigonInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val input: EditText
    private val errorLabel: TextView

    init {
        orientation = VERTICAL
        inflate(context, R.layout.view_origon_input, this)
        input = findViewById(R.id.input_field)
        errorLabel = findViewById(R.id.input_error)
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (errorLabel.isVisible) clearError()
            }
        })
    }

    var text: String
        get() = input.text?.toString().orEmpty()
        set(value) = input.setText(value)

    fun setHint(hint: String) { input.hint = hint }

    fun onImeGo(action: () -> Unit) {
        input.setOnEditorActionListener { _, _, _ -> action(); true }
    }

    fun setError(message: String?) {
        if (message == null) {
            clearError()
            return
        }
        input.setBackgroundResource(R.drawable.bg_input_pill_error)
        errorLabel.text = message
        errorLabel.isVisible = true
        shake()
        Haptics.error(context)
    }

    private fun clearError() {
        input.setBackgroundResource(R.drawable.bg_input_pill)
        errorLabel.isVisible = false
    }
}
