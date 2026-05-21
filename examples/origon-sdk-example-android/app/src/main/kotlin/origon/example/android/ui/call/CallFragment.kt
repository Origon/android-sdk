package origon.example.android.ui.call

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import origon.example.android.MainActivity
import origon.example.android.R
import origon.example.android.databinding.FragmentCallBinding
import origon.example.android.services.CallService
import origon.example.android.ui.chat.RootChatFragment
import origon.example.android.ui.common.applyPressScale

/**
 * Active voice-call surface. Starts a call on appearance, reflects
 * [CallService.Phase] into the UI, and supports mute / end. Mirrors the
 * iOS CallView (minus the multi-layer entrance choreography).
 */
class CallFragment : Fragment(R.layout.fragment_call) {

    private var _binding: FragmentCallBinding? = null
    private val binding get() = _binding!!

    private val main get() = requireActivity() as MainActivity
    private val call get() = main.sdk.call

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentCallBinding.bind(view)

        binding.btnMute.applyPressScale()
        binding.btnMute.setOnClickListener { call.setMute(!call.muted.value) }

        binding.btnEnd.applyPressScale()
        binding.btnEnd.setOnClickListener {
            call.endCall()
            close()
        }

        observe()

        // Kick off the call.
        viewLifecycleOwner.lifecycleScope.launch { runCatching { call.startCall() } }
    }

    override fun onDestroyView() {
        call.reset()
        _binding = null
        super.onDestroyView()
    }

    private fun observe() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    call.phase.collect { phase -> handlePhase(phase) }
                }
                launch {
                    call.muted.collect { muted ->
                        binding.muteIcon.setImageResource(
                            if (muted) R.drawable.ic_mic_muted else R.drawable.ic_mic
                        )
                        binding.muteBg.setBackgroundResource(
                            if (muted) R.drawable.bg_circle_red else R.drawable.bg_circle_muted
                        )
                    }
                }
                launch {
                    call.lastError.collect { error ->
                        if (error != null && call.phase.value is CallService.Phase.Connected) {
                            binding.callError.isVisible = true
                            binding.callError.text = error
                        }
                    }
                }
            }
        }
    }

    private fun handlePhase(phase: CallService.Phase) {
        when (phase) {
            is CallService.Phase.Connected -> startTimer()
            is CallService.Phase.Ended -> {
                if (phase.reason == null) {
                    close()
                } else {
                    binding.callError.isVisible = true
                    binding.callError.text = phase.reason
                    binding.callTimer.isVisible = false
                }
            }
            else -> Unit
        }
    }

    private fun startTimer() {
        viewLifecycleOwner.lifecycleScope.launch {
            delay(1000)
            if (call.phase.value !is CallService.Phase.Connected) return@launch
            var seconds = 1
            binding.callTimer.isVisible = true
            while (isActive && call.phase.value is CallService.Phase.Connected) {
                binding.callTimer.text = formatDuration(seconds)
                delay(1000)
                seconds++
            }
        }
    }

    private fun formatDuration(total: Int): String = when {
        total < 60 -> "${total}s"
        total < 3600 -> "${total / 60}m ${total % 60}s"
        else -> "${total / 3600}h ${(total % 3600) / 60}m"
    }

    private fun close() {
        (parentFragment as? RootChatFragment)?.onCallClosed()
    }
}
