/*
 * Copyright (C) 2021-2025 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs.privacy

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.Process

/**
 * DRS roadmap phase 3 (privacy & trust, task 2): the runtime half of
 * the PROVABLE network block. The static half lives in the CI canary
 * (ci/privacy_canary.sh: no network primitives in the keyboard core,
 * manifest permission allowlist, cleartext denied). This class turns
 * the claim "this app is not moving any bytes" into a number the user
 * can read on screen, at any moment, from the privacy dashboard.
 *
 * Mechanics: TrafficStats exposes per-UID byte counters (our UID =
 * this app — a keyboard process, not the recognizer service's UID;
 * cloud speech traffic bills the Google app's UID, not ours, which is
 * exactly why our counters staying at zero is the right proof for the
 * claim WE make: "this app's own code moves no bytes"). Counters are
 * cumulative-since-boot and can be device-dependent, so the sentinel
 * is honest in two directions:
 *   - DELTA semantics: attestation reports the delta since the
 *     process-level baseline (captured once at app start) and since
 *     the previous attestation — the numbers that can only move if
 *     OUR code moved bytes during this session.
 *   - UNSUPPORTED semantics: when a ROM reports TrafficStats.UNSUPPORTED
 *     (-1) the attestation says so explicitly instead of claiming a
 *     zero it cannot prove.
 *
 * On top of the counters it assembles the permission inventory (what
 * the manifest requests and what the OS actually granted) — the second
 * half of "verifiable": a user can compare the on-screen list with the
 * system settings page and with PRIVACY_POLICY.md.
 *
 * Core logic is pure (injectable readers); only the android() factory
 * touches the framework.
 */
class DrsNetworkSentinel(
    private val traffic: () -> TrafficSnapshot,
    private val permissions: () -> List<PermissionGrant>,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    data class TrafficSnapshot(val rxBytes: Long, val txBytes: Long, val supported: Boolean)

    data class PermissionGrant(val name: String, val granted: Boolean)

    /**
     * The verifiable statement the dashboard renders. [baseline] is the
     * process-start snapshot; when absent the absolute counters are
     * reported as-is. [previous] enables the "since last look" delta.
     */
    data class Attestation(
        val absoluteMode: Boolean,
        val rxSinceBaseline: Long,
        val txSinceBaseline: Long,
        val rxSincePrevious: Long,
        val txSincePrevious: Long,
        val countersSupported: Boolean,
        val zeroTraffic: Boolean,
        val permissions: List<PermissionGrant>,
        val checkedAt: Long,
    )

    private var firstSnapshot: TrafficSnapshot? = null
    private var previous: TrafficSnapshot? = null

    /**
     * Builds the current attestation. `zeroTraffic` is TRUE only when
     * the counters are supported AND every delta is exactly zero — the
     * only combination that honestly reads as "no bytes moved".
     *
     * Baseline precedence: an explicitly passed [baseline] (the
     * process-start snapshot) wins; otherwise the FIRST snapshot this
     * sentinel instance ever took serves as the baseline, so repeated
     * attestation calls always report deltas against a stable origin —
     * never against `now`, which would fake a zero.
     */
    @Synchronized
    fun attestation(
        absoluteMode: Boolean,
        baseline: TrafficSnapshot? = null,
        permissionsNow: List<PermissionGrant>? = null,
    ): Attestation {
        val now = traffic()
        if (firstSnapshot == null) firstSnapshot = now
        val base = baseline ?: firstSnapshot ?: now
        val prev = previous ?: base
        val dRxBase = (now.rxBytes - base.rxBytes).coerceAtLeast(0L)
        val dTxBase = (now.txBytes - base.txBytes).coerceAtLeast(0L)
        val dRxPrev = (now.rxBytes - prev.rxBytes).coerceAtLeast(0L)
        val dTxPrev = (now.txBytes - prev.txBytes).coerceAtLeast(0L)
        previous = now
        val perms = permissionsNow ?: permissions()
        val zero = now.supported && dRxBase == 0L && dTxBase == 0L
        return Attestation(
            absoluteMode = absoluteMode,
            rxSinceBaseline = dRxBase,
            txSinceBaseline = dTxBase,
            rxSincePrevious = dRxPrev,
            txSincePrevious = dTxPrev,
            countersSupported = now.supported,
            zeroTraffic = zero,
            permissions = perms,
            checkedAt = clock(),
        )
    }

    companion object {

        /**
         * The manifest allowlist, duplicated here as the runtime check.
         * ci/privacy_canary.sh owns the build-time copy; if the two ever
         * disagree, the DrsP3PrivacyTests manifest-contract test fails
         * the build before either ships.
         */
        val EXPECTED_PERMISSIONS: List<String> = listOf(
            "android.permission.VIBRATE",
            "android.permission.RECORD_AUDIO",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.INTERNET",
            "android.permission.REQUEST_INSTALL_PACKAGES",
        )

        /** The permissions that could, in principle, reach the network. */
        val NETWORK_CAPABLE_PERMISSIONS: List<String> = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
        )

        /** Process-level baseline — call once from DrsApplication.onCreate. */
        @Volatile
        private var processBaseline: TrafficSnapshot? = null

        fun captureBaseline() {
            processBaseline = readTrafficSnapshot()
        }

        fun baseline(): TrafficSnapshot? = processBaseline

        /** Android factory: TrafficStats for our UID + PackageManager inventory. */
        fun android(context: Context): DrsNetworkSentinel = DrsNetworkSentinel(
            traffic = ::readTrafficSnapshot,
            permissions = {
                val pm = context.applicationContext.packageManager
                val pkg = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                val requested = pkg.requestedPermissions ?: emptyArray()
                val grantedFlags = pkg.requestedPermissionsFlags ?: IntArray(0)
                requested.mapIndexed { i, name ->
                    PermissionGrant(
                        name = name,
                        granted = i < grantedFlags.size &&
                            (grantedFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0,
                    )
                }
            },
        )

        private fun readTrafficSnapshot(): TrafficSnapshot {
            val uid = Process.myUid()
            val rx = TrafficStats.getUidRxBytes(uid)
            val tx = TrafficStats.getUidTxBytes(uid)
            val supported = rx >= 0 && tx >= 0
            return TrafficSnapshot(
                rxBytes = if (rx < 0) 0L else rx,
                txBytes = if (tx < 0) 0L else tx,
                supported = supported,
            )
        }
    }
}
