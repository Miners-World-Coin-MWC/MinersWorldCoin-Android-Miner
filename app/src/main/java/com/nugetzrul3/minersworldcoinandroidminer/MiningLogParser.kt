package com.nugetzrul3.minersworldcoinandroidminer

object MiningLogParser {

    private val HASHRATE_REGEX =
        Regex("""accepted:\s+\d+/\d+\s+\([\d.]+%\),\s+([\d.]+)\s+hash/s""")

    fun isAccepted(log: String): Boolean = log.contains("(yay!!!)")

    fun isRejected(log: String): Boolean = log.contains("(booooo)")

    /** A share line also carries the hashrate, so this must work on any log line. */
    fun parseHashrate(log: String): String? =
        HASHRATE_REGEX.find(log)?.groupValues?.get(1)?.let { "$it H/s" }
}
