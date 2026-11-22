package com.nugetzrul3.minersworldcoinandroidminer

object MiningLogParser {

    private val HASHRATE_REGEX =
        Regex("""accepted:\s+\d+/\d+\s+\([\d.]+%\),\s+([\d.]+)\s+hash/s""")

    private val TOTAL_HASHRATE_REGEX =
        Regex("""Total:\s*([\d.]+)\s+hash/s""")

    private val CONNECTION_FAILURE_MARKERS = listOf(
        "stratum connection failed",
        "stratum connection timed out",
        "stratum connection interrupted",
        "stratum authentication failed",
        "json_rpc_call failed",
        "work retrieval failed",
        "retry after",
        "server requested reconnection"
    )

    fun isAccepted(log: String): Boolean = log.contains("(yay!!!)")

    fun isRejected(log: String): Boolean = log.contains("(booooo)")

    /** Native cpuminer emits this as soon as all worker threads are launched. */
    fun isMinerStarted(log: String): Boolean = log.contains("miner threads started")

    /** A share line also carries the hashrate, so this must work on any log line. */
    fun parseHashrate(log: String): String? =
        HASHRATE_REGEX.find(log)?.groupValues?.get(1)?.let { "$it H/s" }

    /** Periodic native summary, available before the first accepted share. */
    fun parseTotalHashrate(log: String): String? =
        TOTAL_HASHRATE_REGEX.find(log)?.groupValues?.get(1)?.let { "$it H/s" }

    /** Share submissions are proof that the pool connection is working. */
    fun isConnected(log: String): Boolean =
        isAccepted(log) || isRejected(log) || isMinerStarted(log)

    fun isConnectionFailure(log: String): Boolean =
        CONNECTION_FAILURE_MARKERS.any { log.contains(it, ignoreCase = true) }
}
