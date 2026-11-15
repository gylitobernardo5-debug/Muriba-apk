package com.example.vpn

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pool de memória pré-alocada para reduzir Garbage Collection e cópias no caminho rápido TUN <-> SSH:
 * - Cada slot tem capacidade fixa de 2048 bytes (suficiente para IP + TCP + MSS 1460)
 * - Se o pool esgotar sob picos extremos, contabiliza backpressure sem perder pacotes críticos
 */
class PacketBufferPool(
    val slotCount: Int,
    val slotCapacity: Int = 2048
) {
    class Slot(val data: ByteArray) {
        @Volatile
        var length: Int = 0
        var pooled: Boolean = true
    }

    private val freeQueue = ArrayBlockingQueue<Slot>(slotCount)
    private val overflowAllocations = AtomicInteger(0)

    init {
        for (i in 0 until slotCount) {
            freeQueue.offer(Slot(ByteArray(slotCapacity)))
        }
    }

    fun acquireBlocking(timeoutMs: Long = 20L): Slot {
        val quick = freeQueue.poll()
        if (quick != null) return quick
        val waited = freeQueue.poll(timeoutMs, TimeUnit.MILLISECONDS)
        if (waited != null) return waited
        overflowAllocations.incrementAndGet()
        return Slot(ByteArray(slotCapacity)).apply { pooled = false }
    }

    fun acquireImmediateOrFallback(): Slot {
        return freeQueue.poll() ?: run {
            overflowAllocations.incrementAndGet()
            Slot(ByteArray(slotCapacity)).apply { pooled = false }
        }
    }

    fun release(slot: Slot) {
        if (!slot.pooled) return
        slot.length = 0
        freeQueue.offer(slot)
    }

    fun overflowCount(): Int = overflowAllocations.get()
}
