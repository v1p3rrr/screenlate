package com.vpr.screenlate.diagnostics

/**
 * An uncaught exception as types and frames only: messages are left out, since they may quote recognized text or
 * looked-up words.
 */
internal object Crashes {
    private const val FRAMES = 16
    private const val CAUSES = 4

    fun describe(error: Throwable): Map<String, Any?> {
        val chain = generateSequence(error) { it.cause.takeIf { cause -> cause !== it } }.take(CAUSES + 1).toList()
        return linkedMapOf(
            "type" to error.javaClass.name,
            "causes" to chain.drop(1).map { it.javaClass.name }.ifEmpty { null },
            "frames" to error.stackTrace.take(FRAMES).map(::frame),
            "root_frames" to chain.last().takeIf { it !== error }?.stackTrace?.take(FRAMES)?.map(::frame),
        )
    }

    internal fun frame(element: StackTraceElement): String =
        "${element.className}.${element.methodName}:${if (element.isNativeMethod) "native" else element.lineNumber}"
}
