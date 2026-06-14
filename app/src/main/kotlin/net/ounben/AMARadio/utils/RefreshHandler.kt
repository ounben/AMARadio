package net.ounben.AMARadio.utils

import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference

class RefreshHandler {
    private val handler = Handler(Looper.getMainLooper())
    private var runnableDecorator: RunnableDecorator<*>? = null

    fun <T> executePeriodically(task: ObjectBoundRunnable<T>, interval: Long) {
        runnableDecorator?.let { handler.removeCallbacks(it) }
        runnableDecorator = RunnableDecorator(task, interval)
        handler.post(runnableDecorator!!)
    }

    fun cancel() {
        runnableDecorator?.let { handler.removeCallbacks(it) }
        runnableDecorator = null
    }

    private inner class RunnableDecorator<T>(private val runnable: ObjectBoundRunnable<T>, private val interval: Long) : Runnable {
        override fun run() {
            runnable.run()
            if (runnable.objectRef.get() != null && !runnable.terminate) {
                handler.postDelayed(this, interval)
            } else {
                handler.removeCallbacks(this)
                runnableDecorator = null
            }
        }
    }

    abstract class ObjectBoundRunnable<T>(obj: T) : Runnable {
        internal val objectRef = WeakReference(obj)
        internal var terminate = false

        override fun run() {
            val obj = objectRef.get()
            if (obj != null) {
                run(obj)
            }
        }

        protected fun terminate() {
            terminate = true
        }

        protected abstract fun run(obj: T)
    }
}
