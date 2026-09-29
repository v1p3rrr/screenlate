package com.vpr.screenlate.dictionary.api

/**
 * A value computed once per generation of the dictionaries ([registry.DictionaryRepository.generation]). The
 * generation is read before computing, so a change during the computation only makes the next call compute again.
 */
internal class PerGeneration<T> {
    @Volatile
    private var cached: Cached<T>? = null

    suspend fun get(generation: Int, compute: suspend () -> T): T {
        cached?.takeIf { it.generation == generation }?.let { return it.value }
        return compute().also { cached = Cached(generation, it) }
    }

    private class Cached<T>(val generation: Int, val value: T)
}
