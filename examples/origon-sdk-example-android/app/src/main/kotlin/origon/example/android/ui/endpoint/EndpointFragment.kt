package origon.example.android.ui.endpoint

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import ai.origon.sdk.SessionException
import kotlinx.coroutines.launch
import origon.example.android.MainActivity
import origon.example.android.R
import origon.example.android.databinding.FragmentEndpointBinding
import origon.example.android.ui.common.ToastController
import origon.example.android.ui.common.applyPressScale
import origon.example.android.util.SdkErrorKinds

/**
 * Endpoint-login screen. Takes a URL, hands it to the SDK via
 * `SDKManager.initialize(endpoint)`, and on success tells [MainActivity]
 * to persist it and move on to the chat surface.
 */
class EndpointFragment : Fragment(R.layout.fragment_endpoint) {

    private var _binding: FragmentEndpointBinding? = null
    private val binding get() = _binding!!
    private lateinit var toast: ToastController

    private val main get() = requireActivity() as MainActivity

    private var isLoading = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentEndpointBinding.bind(view)
        toast = ToastController(binding.toast)

        binding.endpointInput.setHint(getString(R.string.endpoint_hint))
        binding.endpointInput.onImeGo { handleContinue() }

        binding.continueButton.applyPressScale()
        binding.continueButton.setOnClickListener { handleContinue() }

        // Focus the input + raise the keyboard shortly after the screen settles.
        view.postDelayed({ focusInput() }, 350)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun focusInput() {
        val field = binding.endpointInput.findViewById<View>(R.id.input_field)
        field.requestFocus()
        requireContext().getSystemService<InputMethodManager>()
            ?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun handleContinue() {
        if (isLoading) return
        val trimmed = binding.endpointInput.text.trim()
        if (trimmed.isEmpty()) {
            binding.endpointInput.setError(getString(R.string.endpoint_empty_error))
            return
        }
        binding.endpointInput.setError(null)
        setLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                main.sdk.initialize(endpoint = trimmed)
                setLoading(false)
                main.onEndpointAuthenticated(trimmed)
            } catch (e: SessionException) {
                setLoading(false)
                toast.show(e.userFacingMessage())
            } catch (e: Throwable) {
                setLoading(false)
                toast.show(getString(R.string.endpoint_connect_failed))
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        isLoading = loading
        binding.continueLabel.visibility = if (loading) View.GONE else View.VISIBLE
        binding.continueProgress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.continueButton.alpha = if (loading) 0.5f else 1f
    }

    /** Short, user-facing message for a [SessionException] on this screen. */
    private fun SessionException.userFacingMessage(): String = when (kind) {
        SdkErrorKinds.MISSING_FIELD ->
            "Missing ${code ?: "field"}. Please enter a valid endpoint."
        SdkErrorKinds.HTTP -> {
            if (statusCode == 403 && code == "bundle_id_not_allowed")
                "This app isn't authorized for that endpoint."
            else message?.takeIf { it.isNotEmpty() } ?: "Server error (HTTP $statusCode)."
        }
        SdkErrorKinds.SERVER_UNAVAILABLE -> "Server unavailable. Please try again shortly."
        SdkErrorKinds.OTHER ->
            message?.takeIf { it.isNotEmpty() }
                ?: "Can't reach the server. Check the URL and your connection."
        else -> message ?: getString(R.string.endpoint_connect_failed)
    }
}
