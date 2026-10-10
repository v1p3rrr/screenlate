package com.vpr.screenlate.core.common.language

/**
 * A region whose accent Wiktionary recordings name in their file titles (`En-us-water.ogg`); [codes] are the codes
 * used there. [OTHER] stands for every recording without a listed code.
 */
data class AudioRegion(val id: String, val codes: Set<String> = emptySet()) {
    companion object {
        val OTHER = AudioRegion("other")

        /** [defaults] in the order of the ids in [stored]; regions [stored] lacks follow in their default order. */
        fun ordered(stored: List<String>, defaults: List<AudioRegion>): List<AudioRegion> {
            val byId = defaults.associateBy { it.id }
            val chosen = stored.distinct().mapNotNull(byId::get)
            return chosen + defaults.filter { it !in chosen }
        }
    }
}
