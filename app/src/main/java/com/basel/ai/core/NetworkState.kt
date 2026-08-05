package com.basel.ai.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Reads the connection.
 *
 * Metered versus unmetered is the distinction that matters here, not Wi-Fi
 * versus mobile. Two features in this app move real data — reading the top
 * search results in full, and uploading a page image per unreadable PDF page —
 * and both were happy to do it on a mobile plan without ever asking. A phone
 * that quietly spends the user's data allowance is not being helpful.
 *
 * `isActiveNetworkMetered` is the right question because it also catches a
 * metered Wi-Fi hotspot, which is a phone sharing its own mobile data — the
 * exact case a naive "is it Wi-Fi?" check gets backwards.
 */
object NetworkState {

    fun read(context: Context): Connection {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return Connection.NONE

        return runCatching {
            val network = manager.activeNetwork ?: return Connection.NONE
            val caps = manager.getNetworkCapabilities(network) ?: return Connection.NONE

            // Validated, not merely connected: a captive portal reports a
            // connection and answers every request with a login page, which is
            // worse than being offline because it looks like a parse failure.
            val usable = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (!usable) return Connection.NONE

            if (manager.isActiveNetworkMetered) Connection.METERED else Connection.UNMETERED
        }.getOrDefault(Connection.NONE)
    }
}
