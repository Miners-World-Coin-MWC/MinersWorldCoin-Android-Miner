package com.nugetzrul3.minersworldcoinandroidminer

import org.junit.Assert.assertEquals
import org.junit.Test

class DonationConfigTest {

    @Test
    fun mwcAlgorithmUsesMwcDonationAddress() {
        assertEquals(DonationConfig.MWC_WALLET, DonationConfig.walletFor("ALGO_MWC_YESPOWER_1_0_1"))
    }

    @Test
    fun advcAlgorithmUsesAdvcDonationAddress() {
        assertEquals(DonationConfig.ADVC_WALLET, DonationConfig.walletFor("ALGO_ADVC_YESPOWER_1_0_1"))
    }

    @Test
    fun cycleDurationsAreSixtyAndFiveMinutes() {
        assertEquals(60L * 60 * 1000, DonationConfig.INTERVAL_MS)
        assertEquals(5L * 60 * 1000, DonationConfig.DURATION_MS)
    }
}
