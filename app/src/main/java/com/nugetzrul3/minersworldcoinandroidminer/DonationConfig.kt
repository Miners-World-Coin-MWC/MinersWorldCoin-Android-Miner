package com.nugetzrul3.minersworldcoinandroidminer

/** Settings for the optional "support development" mining window. */
object DonationConfig {

    const val MWC_WALLET = "96geMyKf4fptuj2FhwNCMueZ1kLJmnEdMw"
    const val ADVC_WALLET = "AVarUYRhbaAj2JY3t1Hm8ZP81QX2948dqq"

    const val INTERVAL_MS = 60L * 60 * 1000  // mine for the user for 60 minutes
    const val DURATION_MS = 5L * 60 * 1000   // then donate for 5 minutes

    /** Kept as a pure function so the address selection can be unit tested. */
    fun walletFor(algorithm: String): String =
        if (algorithm.contains("ADVC", ignoreCase = true)) ADVC_WALLET else MWC_WALLET
}
