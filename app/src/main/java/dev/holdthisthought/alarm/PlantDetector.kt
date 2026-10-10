package dev.holdthisthought.alarm

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.common.MlKit
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device check for "is there a plant in this photo?". The image never leaves the phone. */
object PlantDetector {
    private val plantWords = listOf("plant", "flower", "leaf", "tree", "garden", "moss", "petal", "grass", "flowerpot", "houseplant", "bonsai", "cactus", "herb")

    data class Result(val isPlant: Boolean, val labels: List<Pair<String, Float>>)

    suspend fun check(context: Context, bitmap: Bitmap): Result = suspendCancellableCoroutine { cont ->
        // ML Kit's startup provider is removed from the manifest to keep app launch fast.
        runCatching { MlKit.initialize(context.applicationContext) } // throws if already initialised
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.5f).build())
        labeler.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { labels ->
                val list = labels.map { it.text to it.confidence }
                val plant = list.any { (text, confidence) ->
                    confidence >= 0.6f && plantWords.any { text.contains(it, ignoreCase = true) }
                }
                cont.resume(Result(plant, list))
                labeler.close()
            }
            .addOnFailureListener {
                labeler.close()
                cont.resumeWithException(it)
            }
    }
}
