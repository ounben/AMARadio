package com.ounben.amaradio.utils

import com.ounben.amaradio.Utils
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

abstract class CustomFilter {
    private var mDelayer: Delayer? = null
    private val mLock = Any()
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var filterJob: Job? = null
    private val filterRequestId = AtomicInteger(0)

    fun filter(constraint: CharSequence?) {
        filter(constraint, null)
    }

    fun filter(constraint: CharSequence?, listener: FilterListener?) {
        val delayValue = synchronized(mLock) {
            mDelayer?.getPostingDelay(constraint) ?: 0
        }
        
        filterJob?.cancel()
        val requestId = filterRequestId.incrementAndGet()
        
        filterJob = scope.launch {
            if (delayValue > 0) delay(delayValue.milliseconds)
            
            val results = withContext(Dispatchers.IO) {
                try {
                    performFiltering(constraint)
                } catch (e: Exception) {
                    if (Utils.isDebug) e.printStackTrace()
                    FilterResults()
                }
            }
            
            // Check if this is still the latest request
            if (requestId == filterRequestId.get()) {
                publishResults(constraint, results)
                listener?.onFilterComplete(results.count)
            }
        }
    }

    protected abstract suspend fun performFiltering(constraint: CharSequence?): FilterResults
    protected abstract fun publishResults(constraint: CharSequence?, results: FilterResults)

    open class FilterResults {
        var values: Any? = null
        var count = 0
    }

    interface FilterListener {
        fun onFilterComplete(count: Int)
    }

    interface Delayer {
        fun getPostingDelay(constraint: CharSequence?): Long
    }
}
