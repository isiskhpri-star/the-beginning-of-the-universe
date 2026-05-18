package llm

import llm.models.GenerationRequest
import llm.models.GenerationResult
import llm.models.GenerationStatus
import llm.models.RequestPriority
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages background scheduling and execution of [GenerationRequest]s.
 *
 * The scheduler maintains a priority queue of pending requests, processes
 * them sequentially (or in configurable batches), and tracks lifecycle
 * state so callers can query progress, cancel requests, or drain the queue.
 *
 * Usage:
 * ```kotlin
 * val engine = BackgroundLLMEngine()
 * val scheduler = BackgroundLLMScheduler(engine)
 *
 * scheduler.submit(request1)
 * scheduler.submit(request2)
 *
 * // Process all pending requests
 * val results = scheduler.processQueue()
 * ```
 *
 * @property engine The [BackgroundLLMEngine] that processes individual requests.
 * @property maxQueueSize Maximum number of requests allowed in the queue.
 * @property maxRetries Maximum number of retry attempts for failed requests.
 */
class BackgroundLLMScheduler(
    private val engine: BackgroundLLMEngine,
    private val maxQueueSize: Int = 1000,
    private val maxRetries: Int = 3
) {

    /**
     * Internal wrapper that pairs a request with scheduling metadata.
     */
    private data class ScheduledRequest(
        val request: GenerationRequest,
        val submittedAt: Instant = Instant.now(),
        val retryCount: Int = 0
    ) : Comparable<ScheduledRequest> {
        override fun compareTo(other: ScheduledRequest): Int {
            val priorityCompare = request.priority.ordinal.compareTo(other.request.priority.ordinal)
            if (priorityCompare != 0) return priorityCompare
            return submittedAt.compareTo(other.submittedAt)
        }
    }

    private val queue: PriorityBlockingQueue<ScheduledRequest> = PriorityBlockingQueue()
    private val processing: AtomicBoolean = AtomicBoolean(false)
    private val cancelledIds: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()
    private val completedResults: ConcurrentLinkedDeque<GenerationResult> = ConcurrentLinkedDeque()
    private val totalProcessed: AtomicInteger = AtomicInteger(0)
    private val totalFailed: AtomicInteger = AtomicInteger(0)
    private val totalProcessingTimeMs: AtomicLong = AtomicLong(0)

    /**
     * Submits a generation request to the background queue.
     *
     * @param request The request to enqueue.
     * @return True if the request was accepted, false if the queue is full.
     */
    fun submit(request: GenerationRequest): Boolean {
        if (queue.size >= maxQueueSize) return false
        queue.offer(ScheduledRequest(request))
        return true
    }

    /**
     * Submits multiple requests to the queue.
     *
     * @return The number of requests that were accepted.
     */
    fun submitBatch(requests: List<GenerationRequest>): Int =
        requests.count { submit(it) }

    /**
     * Processes all pending requests in priority order and returns the results.
     *
     * This method blocks until the queue is drained. Cancelled requests are
     * skipped. Failed requests are retried up to [maxRetries] times.
     */
    fun processQueue(): List<GenerationResult> {
        if (!processing.compareAndSet(false, true)) {
            return emptyList()
        }

        val batchResults = mutableListOf<GenerationResult>()

        try {
            while (queue.isNotEmpty()) {
                val scheduled = queue.poll() ?: break

                if (cancelledIds.containsKey(scheduled.request.requestId)) {
                    val cancelled = GenerationResult(
                        requestId = scheduled.request.requestId,
                        status = GenerationStatus.CANCELLED,
                        errorMessage = "Request was cancelled before processing."
                    )
                    batchResults.add(cancelled)
                    completedResults.addLast(cancelled)
                    continue
                }

                val result = engine.process(scheduled.request)
                totalProcessingTimeMs.addAndGet(result.durationMs)

                if (result.status == GenerationStatus.ERROR &&
                    scheduled.retryCount < maxRetries
                ) {
                    queue.offer(scheduled.copy(retryCount = scheduled.retryCount + 1))
                    totalFailed.incrementAndGet()
                } else {
                    batchResults.add(result)
                    completedResults.addLast(result)
                    totalProcessed.incrementAndGet()
                    if (result.status == GenerationStatus.ERROR) {
                        totalFailed.incrementAndGet()
                    }
                }
            }
        } finally {
            processing.set(false)
        }

        return batchResults
    }

    /**
     * Processes up to [limit] requests from the queue.
     */
    fun processNext(limit: Int = 1): List<GenerationResult> {
        if (!processing.compareAndSet(false, true)) {
            return emptyList()
        }

        val batchResults = mutableListOf<GenerationResult>()
        var processed = 0

        try {
            while (processed < limit && queue.isNotEmpty()) {
                val scheduled = queue.poll() ?: break

                if (cancelledIds.containsKey(scheduled.request.requestId)) {
                    val cancelled = GenerationResult(
                        requestId = scheduled.request.requestId,
                        status = GenerationStatus.CANCELLED,
                        errorMessage = "Request was cancelled before processing."
                    )
                    batchResults.add(cancelled)
                    completedResults.addLast(cancelled)
                    processed++
                    continue
                }

                val result = engine.process(scheduled.request)
                totalProcessingTimeMs.addAndGet(result.durationMs)

                if (result.status == GenerationStatus.ERROR &&
                    scheduled.retryCount < maxRetries
                ) {
                    queue.offer(scheduled.copy(retryCount = scheduled.retryCount + 1))
                    totalFailed.incrementAndGet()
                } else {
                    batchResults.add(result)
                    completedResults.addLast(result)
                    totalProcessed.incrementAndGet()
                    if (result.status == GenerationStatus.ERROR) {
                        totalFailed.incrementAndGet()
                    }
                }
                processed++
            }
        } finally {
            processing.set(false)
        }

        return batchResults
    }

    /**
     * Cancels a pending request by ID. If the request is already being
     * processed, the cancellation takes effect on retry.
     */
    fun cancel(requestId: String) {
        cancelledIds[requestId] = true
    }

    /**
     * Returns the number of requests currently in the queue.
     */
    fun queueSize(): Int = queue.size

    /**
     * Returns true if the scheduler is currently processing requests.
     */
    fun isProcessing(): Boolean = processing.get()

    /**
     * Returns scheduler statistics for monitoring.
     */
    fun getStats(): SchedulerStats = SchedulerStats(
        queueDepth = queue.size,
        totalProcessed = totalProcessed.get(),
        totalFailed = totalFailed.get(),
        totalProcessingTimeMs = totalProcessingTimeMs.get(),
        isProcessing = processing.get(),
        completedResultCount = completedResults.size
    )

    /**
     * Returns all completed results, newest first.
     */
    fun getCompletedResults(): List<GenerationResult> =
        completedResults.toList().reversed()

    /**
     * Clears the pending queue. Already-processing requests are not affected.
     */
    fun clearQueue() {
        queue.clear()
    }
}

/**
 * Snapshot of scheduler health and throughput metrics.
 *
 * @property queueDepth Number of requests waiting to be processed.
 * @property totalProcessed Total requests processed since scheduler creation.
 * @property totalFailed Total requests that failed (including retries).
 * @property totalProcessingTimeMs Cumulative processing time across all requests.
 * @property isProcessing Whether the scheduler is currently running.
 * @property completedResultCount Number of completed results stored.
 */
data class SchedulerStats(
    val queueDepth: Int,
    val totalProcessed: Int,
    val totalFailed: Int,
    val totalProcessingTimeMs: Long,
    val isProcessing: Boolean,
    val completedResultCount: Int
) {
    /**
     * Average processing time per request in milliseconds, or 0 if none processed.
     */
    fun averageProcessingTimeMs(): Long =
        if (totalProcessed > 0) totalProcessingTimeMs / totalProcessed else 0
}
