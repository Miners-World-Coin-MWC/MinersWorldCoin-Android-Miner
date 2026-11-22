package com.nugetzrul3.minersworldcoinandroidminer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue

class UtilsTest {

    @Test
    fun rotateStringQueue_keepsNewestEntries() {
        val queue = LinkedBlockingQueue<String?>(3)

        Utils.rotateStringQueue(queue, "line 1")
        Utils.rotateStringQueue(queue, "line 2")
        Utils.rotateStringQueue(queue, "line 3")
        val result = Utils.rotateStringQueue(queue, "line 4")

        assertEquals("line 2line 3line 4", result)
    }

    @Test
    fun rotateStringQueue_handlesEmptyMessage() {
        val queue = LinkedBlockingQueue<String?>(2)

        assertEquals("", Utils.rotateStringQueue(queue, ""))
    }
}
