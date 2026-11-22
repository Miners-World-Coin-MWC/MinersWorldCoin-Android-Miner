package com.nugetzrul3.minersworldcoinandroidminer

import java.util.concurrent.BlockingQueue

object Utils {
    fun rotateStringQueue(queue: BlockingQueue<String?>, next: String?): String {
        while (!queue.offer(next)) {
            try {
                queue.take()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }

        return buildString { for (element in queue) append(element) }
    }
}
