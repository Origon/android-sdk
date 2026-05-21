package origon.example.android.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import origon.example.android.MainActivity
import origon.example.android.R
import origon.example.android.databinding.FragmentRootChatBinding
import origon.example.android.services.SDKManager
import origon.example.android.ui.call.CallFragment
import origon.example.android.ui.common.ToastController
import origon.example.android.ui.common.applyPressScale

/**
 * Hosts the chat surface: a navigation drawer (sidebar of past sessions)
 * over the chat content, plus the voice-call overlay. Boots the SDK on
 * first appearance, mirroring the iOS RootChatView.
 */
class RootChatFragment : Fragment(R.layout.fragment_root_chat) {

    private var _binding: FragmentRootChatBinding? = null
    private val binding get() = _binding!!

    private val main get() = requireActivity() as MainActivity
    private val sdk: SDKManager get() = main.sdk
    private val chat get() = sdk.chat

    private lateinit var messagesAdapter: MessagesAdapter
    private lateinit var sessionsAdapter: SessionsAdapter
    private lateinit var pendingAdapter: PendingAttachmentsAdapter
    private lateinit var toast: ToastController

    private var sessionId: String? = null
    private var booted = false
    private var isSending = false
    private var callActive = false

    // Photo/video picker — returns a content:// uri.
    private val pickMedia = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { enqueueUpload(it) } }

    // Generic file picker.
    private val pickFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { enqueueUpload(it) } }

    // RECORD_AUDIO is a runtime ("dangerous") permission — the manifest
    // declaration is not enough. A voice call's mic capture (AAudio input)
    // fails without it, so the app must request the grant before starting
    // a call. This is the consumer's responsibility, not the SDK's.
    private val requestMic = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCall() else toast.show(getString(R.string.call_mic_required))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentRootChatBinding.bind(view)
        toast = ToastController(binding.toast)

        setupLists()
        setupToolbar()
        setupComposer()
        setupSidebar()
        observeState()
        boot()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    // MARK: - Setup

    private fun setupLists() {
        messagesAdapter = MessagesAdapter(onAttachmentClick = ::openUrl)
        binding.messagesList.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.messagesList.adapter = messagesAdapter

        pendingAdapter = PendingAttachmentsAdapter(onRemove = { chat.removePendingAttachment(it) })
        binding.pendingList.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.pendingList.adapter = pendingAdapter

        sessionsAdapter = SessionsAdapter(onClick = ::pickSession)
        binding.sessionsList.layoutManager = LinearLayoutManager(requireContext())
        binding.sessionsList.adapter = sessionsAdapter
    }

    private fun setupToolbar() {
        binding.btnHistory.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
        binding.btnNewSession.setOnClickListener { startNewSession() }
    }

    private fun setupComposer() {
        binding.btnAttach.setOnClickListener { showAttachMenu() }

        binding.inputMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (s.isNullOrBlank()) chat.stopTyping() else chat.notifyTyping()
                updateSendButton()
            }
        })

        binding.btnSend.applyPressScale()
        binding.btnSend.setOnClickListener {
            if (hasContent()) sendMessage() else startCall()
        }
        updateSendButton()
    }

    private fun setupSidebar() {
        binding.btnOptions.setOnClickListener { anchor ->
            PopupMenu(requireContext(), anchor).apply {
                menu.add(getString(R.string.sidebar_change_endpoint)).setOnMenuItemClickListener {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    binding.root.postDelayed({ main.onChangeEndpoint() }, 120)
                    true
                }
                show()
            }
        }
    }

    // MARK: - State observation

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    chat.messages.collect { messages ->
                        messagesAdapter.submit(messages)
                        val empty = messages.isEmpty() && !chat.isTyping.value
                        binding.emptyState.isVisible = empty
                        binding.messagesList.isVisible = !empty
                        if (messages.isNotEmpty()) {
                            binding.messagesList.scrollToPosition(messages.size - 1)
                        }
                    }
                }
                launch {
                    chat.pendingAttachments.collect { pending ->
                        pendingAdapter.submit(pending)
                        binding.pendingList.isVisible = pending.isNotEmpty()
                        updateSendButton()
                    }
                }
                launch {
                    chat.error.collect { toast.show(it) }
                }
                launch {
                    sdk.sessions.collect { sessionsAdapter.submit(it, sessionId) }
                }
            }
        }
    }

    // MARK: - Boot

    private fun boot() {
        if (booted) return
        booted = true

        if (sdk.isReady.value) {
            showContent()
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching { sdk.getSessions() }
                chat.openSession(null)
            }
            return
        }

        val endpoint = main.currentEndpoint()
        if (endpoint.isNullOrEmpty()) {
            main.onChangeEndpoint()
            return
        }

        showLoading()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                sdk.initialize(endpoint = endpoint)
                runCatching { sdk.getSessions() }
                showContent()
                chat.openSession(null)
            } catch (e: Throwable) {
                showError("Failed to connect: ${e.message}")
            }
        }
    }

    private fun showLoading() {
        binding.loadingLogo.isVisible = true
        binding.errorState.isVisible = false
        binding.drawerLayout.isVisible = false
        binding.loadingLogo.animate()
            .scaleX(1.14f).scaleY(1.14f).alpha(1f).setDuration(800)
            .withEndAction {
                binding.loadingLogo.animate().scaleX(1f).scaleY(1f).alpha(0.5f).setDuration(800).start()
            }.start()
    }

    private fun showError(message: String) {
        binding.loadingLogo.isVisible = false
        binding.drawerLayout.isVisible = false
        binding.errorState.isVisible = true
        binding.errorText.text = message
        binding.btnRetry.applyPressScale()
        binding.btnRetry.setOnClickListener {
            booted = false
            boot()
        }
        binding.btnChangeEndpointError.setOnClickListener { main.onChangeEndpoint() }
    }

    private fun showContent() {
        binding.loadingLogo.isVisible = false
        binding.errorState.isVisible = false
        binding.drawerLayout.isVisible = true
    }

    // MARK: - Session lifecycle

    private fun pickSession(id: String) {
        sessionId = id
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        viewLifecycleOwner.lifecycleScope.launch { chat.openSession(id) }
        sessionsAdapter.submit(sdk.sessions.value, sessionId)
    }

    private fun startNewSession() {
        chat.endCurrentSession()
        sessionId = null
        sessionsAdapter.submit(sdk.sessions.value, sessionId)
    }

    // MARK: - Composer

    private fun hasContent(): Boolean =
        binding.inputMessage.text.isNotBlank() || chat.pendingAttachments.value.isNotEmpty()

    private fun updateSendButton() {
        if (_binding == null) return
        val send = hasContent()
        binding.sendIcon.setImageResource(if (send) R.drawable.ic_send else R.drawable.ic_voice)
    }

    private fun sendMessage() {
        val text = binding.inputMessage.text.toString().trim()
        if (text.isEmpty() && chat.pendingAttachments.value.isEmpty()) return
        viewLifecycleOwner.lifecycleScope.launch {
            if (chat.hasUploadingAttachments) {
                setSending(true)
                while (chat.hasUploadingAttachments) kotlinx.coroutines.delay(100)
                setSending(false)
            }
            binding.inputMessage.setText("")
            chat.sendMessage(text)
        }
    }

    private fun setSending(sending: Boolean) {
        isSending = sending
        binding.sendIcon.isVisible = !sending
        binding.sendProgress.isVisible = sending
    }

    // MARK: - Call

    private fun startCall() {
        hideKeyboard()
        // Gate the call on the mic permission. Without RECORD_AUDIO granted,
        // the SDK's AAudio input stream fails to open and the call has no
        // outgoing audio.
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchCall()
        } else {
            requestMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun launchCall() {
        if (callActive) return
        callActive = true
        binding.callContainer.isVisible = true
        childFragmentManager.beginTransaction()
            .replace(R.id.call_container, CallFragment())
            .commit()
    }

    fun onCallClosed() {
        callActive = false
        binding.callContainer.isVisible = false
        childFragmentManager.findFragmentById(R.id.call_container)?.let {
            childFragmentManager.beginTransaction().remove(it).commit()
        }
    }

    // MARK: - Attachments

    private fun showAttachMenu() {
        hideKeyboard()
        PopupMenu(requireContext(), binding.btnAttach).apply {
            menu.add(getString(R.string.attach_photo_library)).setOnMenuItemClickListener {
                pickMedia.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageAndVideo
                    )
                )
                true
            }
            menu.add(getString(R.string.attach_files)).setOnMenuItemClickListener {
                pickFile.launch(arrayOf("*/*"))
                true
            }
            show()
        }
    }

    private fun enqueueUpload(uri: Uri) {
        val (name, type) = queryFileInfo(uri)
        chat.uploadFile(requireContext().applicationContext, uri, name, type)
    }

    private fun queryFileInfo(uri: Uri): Pair<String, String> {
        val resolver = requireContext().contentResolver
        val type = resolver.getType(uri) ?: "application/octet-stream"
        var name = "file"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = c.getString(idx) ?: name
            }
        }
        return name to type
    }

    // MARK: - Helpers

    private fun openUrl(url: String) {
        runCatching {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext()
            .getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }
}
