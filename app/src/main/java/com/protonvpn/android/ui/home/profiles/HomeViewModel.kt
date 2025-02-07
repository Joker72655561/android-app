/*
 * Copyright (c) 2020 Proton Technologies AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.protonvpn.android.ui.home.profiles

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.map
import com.protonvpn.android.appconfig.ApiNotificationManager
import com.protonvpn.android.appconfig.ApiNotificationTypes
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.profiles.Profile
import com.protonvpn.android.models.profiles.Profile.Companion.getTempProfile
import com.protonvpn.android.models.vpn.VpnCountry
import com.protonvpn.android.tv.main.MainViewModel
import com.protonvpn.android.utils.DebugUtils
import com.protonvpn.android.utils.ProtonLogger
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.utils.UserPlanManager
import com.protonvpn.android.utils.eagerMapNotNull
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnStateMonitor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

class HomeViewModel @Inject constructor(
    val userData: UserData,
    private val apiNotificationManager: ApiNotificationManager,
    private val vpnStateMonitor: VpnStateMonitor,
    private val vpnConnectionManager: VpnConnectionManager,
    private val serverManager: ServerManager,
    userPlanManager: UserPlanManager,
    certificateRepository: CertificateRepository
) : MainViewModel(userData, userPlanManager, certificateRepository) {

    inner class OfferViewModel(
        private val id: String,
        val label: String,
        val url: String,
        val iconUrl: String,
        val visited: Boolean
    ) {
        fun setVisited() = setOfferVisited(id)
    }

    // Wrapper class needed so that storage can use its class as key
    private class VisitedOffers(val visited: MutableSet<String>)
    private var visitedOffersObservable =
            MutableLiveData(Storage.load(VisitedOffers::class.java, VisitedOffers(mutableSetOf())))
    private val visitedOffers get() = visitedOffersObservable.value!!.visited

    val offersViewModel get() = apiNotificationManager.activeListObservable.eagerMapNotNull {
        createApiNotificationViewModel()
    }.apply {
        addSource(visitedOffersObservable) { value = createApiNotificationViewModel() }
    }

    private fun createApiNotificationViewModel(): List<OfferViewModel> =
        apiNotificationManager.activeList.filter {
            it.type == ApiNotificationTypes.TYPE_OFFER
        }.mapNotNull { apiNotification ->
            apiNotification.offer?.let {
                OfferViewModel(apiNotification.id, it.label, it.url, it.iconUrl, apiNotification.id in visitedOffers)
            }
        }

    val haveNonVisitedOffers =
            offersViewModel.map { offer -> offer.any { !it.visited } }

    fun setOfferVisited(id: String) {
        visitedOffers.add(id)
        visitedOffersObservable.value = visitedOffersObservable.value
        Storage.save(visitedOffersObservable.value)
    }

    // Temporary method to help java activity collect a flow
    fun collectPlanChange(activity: AppCompatActivity, onChange: (UserPlanManager.InfoChange.PlanChange) -> Unit) {
        activity.lifecycleScope.launch {
            userPlanChangeEvent.collect {
                onChange(it)
            }
        }
    }

    // Convert to a suspend method and remove the callback once HomeActivity is in Kotlin.
    fun reconnectToSameCountry(connectCallback: (newProfile: Profile) -> Unit) {
        DebugUtils.debugAssert("Is connected") { vpnStateMonitor.isConnected }
        val connectedCountry: String = vpnStateMonitor.connectionParams!!.server.exitCountry
        val exitCountry: VpnCountry? =
            serverManager.getVpnExitCountry(connectedCountry, userData.isSecureCoreEnabled)
        val newServer = if (exitCountry != null) {
            serverManager.getBestScoreServer(exitCountry)
        } else {
            serverManager.getBestScoreServer(userData.isSecureCoreEnabled)
        }
        if (newServer != null) {
            val newProfile = getTempProfile(newServer, serverManager)
            vpnConnectionManager.disconnectWithCallback { connectCallback(newProfile) }
        } else {
            val toOrFrom = if (userData.isSecureCoreEnabled) "to" else "from"
            ProtonLogger.log("Unable to find a server to connect to when switching $toOrFrom Secure Core")
            vpnConnectionManager.disconnect()
        }
    }
}
