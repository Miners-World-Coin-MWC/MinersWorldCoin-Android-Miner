package com.nugetzrul3.minersworldcoinandroidminer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiningLogParserTest {

    @Test
    fun shareLine_reportsResultAndHashrate() {
        val log = "accepted: 1/2 (50.00%), 123.4 hash/s (yay!!!)"

        assertTrue(MiningLogParser.isAccepted(log))
        assertFalse(MiningLogParser.isRejected(log))
        assertEquals("123.4 H/s", MiningLogParser.parseHashrate(log))
    }

    @Test
    fun rejectedShareLine_alsoReportsHashrate() {
        val log = "accepted: 1/3 (33.33%), 98.7 hash/s (booooo)"

        assertTrue(MiningLogParser.isRejected(log))
        assertEquals("98.7 H/s", MiningLogParser.parseHashrate(log))
    }

    @Test
    fun unrelatedLine_hasNoHashrate() {
        assertNull(MiningLogParser.parseHashrate("stratum.connect: connected"))
    }
}
