package origon.example.android

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import origon.example.android.data.StorageKeys
import origon.example.android.services.SDKManager
import origon.example.android.ui.chat.RootChatFragment
import origon.example.android.ui.endpoint.EndpointFragment

/**
 * Single-Activity host. Gates between the Endpoint screen and the chat
 * surface based on the persisted endpoint, mirroring the iOS RootView:
 *   - no endpoint saved  → EndpointFragment
 *   - endpoint present    → RootChatFragment (which boots the SDK)
 */
class MainActivity : AppCompatActivity(R.layout.activity_main) {

    val sdk: SDKManager get() = (application as OrigonExampleApp).sdk

    private val prefs by lazy {
        getSharedPreferences(StorageKeys.PREFS, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            if (currentEndpoint().isNullOrEmpty()) showEndpoint() else showChat()
        }
    }

    fun currentEndpoint(): String? = prefs.getString(StorageKeys.ORIGON_ENDPOINT, null)

    /** Called by EndpointFragment after a successful `sdk.initialize`. */
    fun onEndpointAuthenticated(url: String) {
        prefs.edit { putString(StorageKeys.ORIGON_ENDPOINT, url) }
        showChat()
    }

    /** Called from the sidebar / error state — tears down and returns to Endpoint. */
    fun onChangeEndpoint() {
        sdk.teardown()
        prefs.edit { remove(StorageKeys.ORIGON_ENDPOINT) }
        showEndpoint()
    }

    private fun showEndpoint() = swap(EndpointFragment())
    private fun showChat() = swap(RootChatFragment())

    private fun swap(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
