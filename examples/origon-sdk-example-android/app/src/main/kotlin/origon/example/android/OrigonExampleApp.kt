package origon.example.android

import android.app.Application
import ai.origon.sdk.OrigonClient
import origon.example.android.services.SDKManager

/**
 * Owns the single app-wide [SDKManager]. Every screen reaches the SDK
 * through `(application as OrigonExampleApp).sdk`.
 */
class OrigonExampleApp : Application() {

    lateinit var sdk: SDKManager
        private set

    override fun onCreate() {
        super.onCreate()
        OrigonClient.initLogging("info,session=debug,moq=debug")
        sdk = SDKManager(applicationContext)
    }
}
