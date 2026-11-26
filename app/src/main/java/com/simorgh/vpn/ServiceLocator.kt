package com.simorgh.vpn

import android.app.Application
import com.simorgh.vpn.data.ProfileRepo
import com.simorgh.vpn.data.SimorghSettings
import com.simorgh.vpn.data.SubscriptionRepo
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Tiny manual DI container. Single-process app (v0.1), so a simple object is enough.
 */
object ServiceLocator {

    lateinit var profileRepo: ProfileRepo
        private set
    lateinit var subscriptionRepo: SubscriptionRepo
        private set

    /** Latest settings snapshot, kept up-to-date by [SimorghApp]. */
    val settings = MutableStateFlow(SimorghSettings())

    fun init(app: Application) {
        if (::profileRepo.isInitialized) return
        profileRepo = ProfileRepo(app)
        subscriptionRepo = SubscriptionRepo(app, profileRepo)
    }
}
