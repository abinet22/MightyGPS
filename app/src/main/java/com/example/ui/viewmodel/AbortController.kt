package com.example.ui.viewmodel

import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap

/**
 * A utility class modeled after JS AbortController to intercept and cancel pending requests.
 * It manages active Kotlin Coroutines Jobs and allows registering and canceling them selectively or fully.
 */
class AbortController {
    private val activeJobs = ConcurrentHashMap<String, Job>()

    /**
     * Registers a coroutine job under a specific request key (e.g., "devices", "history").
     * If a pending request with the same key is already active, it is cancelled to prevent race conditions.
     */
    fun register(key: String, job: Job) {
        // Cancel the previous active job for this key
        activeJobs[key]?.let {
            if (it.isActive) {
                it.cancel()
            }
        }
        activeJobs[key] = job
        job.invokeOnCompletion {
            // Only remove if it's the same job instance (to prevent removing a newly registered job)
            activeJobs.remove(key, job)
        }
    }

    /**
     * Cancels the active job associated with a specific key.
     */
    fun abort(key: String) {
        activeJobs[key]?.let {
            if (it.isActive) {
                it.cancel()
            }
        }
        activeJobs.remove(key)
    }

    /**
     * Cancels all pending requests registered with this AbortController.
     */
    fun abortAll() {
        val keys = activeJobs.keys()
        while (keys.hasMoreElements()) {
            val key = keys.nextElement()
            activeJobs[key]?.let {
                if (it.isActive) {
                    it.cancel()
                }
            }
        }
        activeJobs.clear()
    }
}
