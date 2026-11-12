package com.aura.aura_ui.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.aura.mcp.bridge.RestrictedAppsStore

/**
 * Per-app block list. AURA refuses to act while a blocked package is foreground; the refusal is
 * a normal, structured tool error rather than a crash or a silent skip.
 */
@Singleton
class RestrictedAppsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : RestrictedAppsStore {

    private val prefs = context.getSharedPreferences("aura_restricted_apps", Context.MODE_PRIVATE)
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /**
     * Hard block list baked into the app: banking, payments, wallets, brokerages, tax and
     * authenticators. These can never be opened by the agent, regardless of user configuration.
     * Matched against the foreground package; the bridge surfaces the same structured refusal.
     */
    override fun isBlocked(packageName: String): Boolean =
        blockedPackages().contains(packageName) || SYSTEM_BLOCKED_PACKAGES.contains(packageName)

    override fun blockedPackages(): Set<String> =
        SYSTEM_BLOCKED_PACKAGES + prefs.getStringSet(KEY, emptySet()).orEmpty()

    fun setBlocked(packageName: String, blocked: Boolean) {
        val next = blockedPackages().toMutableSet()
        if (blocked) next += packageName else next -= packageName
        prefs.edit().putStringSet(KEY, next).apply()
        _revision.value = _revision.value + 1
    }

    fun clear() {
        prefs.edit().clear().apply()
        _revision.value = _revision.value + 1
    }

    companion object {
        private const val KEY = "blocked_packages"

        /**
         * Banking / payment / brokerage / authenticator packages banned at the system level.
         * AURA should never drive these; the list is a defence-in-depth control, not a UI list.
         */
        val SYSTEM_BLOCKED_PACKAGES: Set<String> = setOf(
            // Google / generic wallets and payments
            "com.google.android.apps.walletnfcrel",
            "com.android.vending",
            // Major authenticator apps
            "com.google.android.apps.authenticator2",
            "com.microsoft.authenticator",
            "com.authy.authy",
            "com.duosecurity.duomobile",
            "com.oneapp.authenticator",
            "com.yubico.authenticator",
            // Tax / government identity
            "com.digilocker.app",
            "in.gov.uidai.mAadhaarPlus",
            // Banking apps (common set; extend via user blocklist)
            "com.mbna.general",
            "com.monzo.android",
            "com.revolut.revolut",
            "com.chase.sig.android",
            "com.bankofamerica",
            "com.wellsfargo.mobile",
            "com.usbank.mobilemoney",
            "com.citi.mobile",
            "com.barclays.myapp",
            "com.barclaycard.android.ua",
            "com.natwest.mobile.banking",
            "com.lloyds.android.bank",
            "com.halifax.mobile.banking",
            "com.santander.app",
            "com.hsbc.uk.personal",
            "com.db.mm.deutschebank",
            "com.ingdirect.android",
            "com.kkb.mobapp.kkbmobile",
            "com.td.us.tdust",
            "com.td.us.national",
            "com.royalbank.cibc",
            "com.anz.digital",
            "com.kookmin.bank",
        )
    }
}
