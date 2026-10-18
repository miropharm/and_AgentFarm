package com.muvusoft.agentfarm

import android.content.Context
import android.os.Debug
import shark.AndroidMetadataExtractor
import shark.AndroidObjectInspectors
import shark.AndroidReferenceMatchers
import shark.FilteringLeakingObjectFinder
import shark.HeapAnalysisFailure
import shark.HeapAnalysisSuccess
import shark.HeapAnalyzer
import shark.OnAnalysisProgressListener
import java.io.File

/**
 * The process's heap read the way LeakCanary reads it, with Shark, its analyzer: every destroyed activity,
 * fragment or view still reachable is a leak, and one held only through a path Android's own code is known for
 * is a library leak. No watcher is installed (LeakCanary refuses a build that is not debuggable, and the
 * emulator tests the release build): the dump itself names what is held after it should have gone.
 */
object Leaks {
    class Found(val app: List<String>, val library: List<String>)

    fun find(ctx: Context): Found {
        repeat(3) {
            Runtime.getRuntime().gc()
            Thread.sleep(300)
            System.runFinalization()
        }
        val file = File(ctx.cacheDir, "leaks.hprof").apply { delete() }
        try {
            Debug.dumpHprofData(file.path)
            val analysis = HeapAnalyzer(OnAnalysisProgressListener.NO_OP).analyze(
                heapDumpFile = file,
                leakingObjectFinder = FilteringLeakingObjectFinder(AndroidObjectInspectors.appLeakingObjectFilters),
                referenceMatchers = AndroidReferenceMatchers.appDefaults,
                objectInspectors = AndroidObjectInspectors.appDefaults,
                metadataExtractor = AndroidMetadataExtractor,
            )
            return when (analysis) {
                is HeapAnalysisSuccess -> Found(
                    app = analysis.applicationLeaks.flatMap { leak -> leak.leakTraces.map { it.toSimplePathString() } },
                    library = analysis.libraryLeaks.map { it.description },
                )
                is HeapAnalysisFailure -> throw AssertionError("the heap dump could not be analyzed", analysis.exception)
            }
        } finally {
            file.delete()
        }
    }
}
