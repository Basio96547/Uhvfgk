package com.basel.ai.llm

import com.basel.ai.core.Localization
import android.content.Context
import com.basel.ai.core.DeviceSnapshot
import com.basel.ai.model.ModelFormat
import com.basel.ai.model.ModelSpec
import com.basel.ai.model.rejectionMessage
import java.io.File

/**
 * Picks the runtime that can actually read a given model file.
 *
 * The two runtimes accept disjoint formats — MediaPipe reads `.task` bundles,
 * llama.cpp reads GGUF — so the choice is made from the file's magic bytes
 * rather than asking the user to know the difference.
 */
object EngineFactory {

    fun load(context: Context, spec: ModelSpec, device: DeviceSnapshot): TextEngine {
        val file = File(spec.path)
        if (!file.exists()) {
            throw ModelLoadException(Localization.strings.modelFileNotFound(spec.path))
        }

        return when (val format = ModelFormat.detect(file)) {
            ModelFormat.GGUF -> LlamaCppEngine.load(context, spec, device)

            ModelFormat.TASK,
            ModelFormat.LITERTLM,
            ModelFormat.TFLITE -> InferenceEngine.load(context, spec, device)

            ModelFormat.UNKNOWN -> throw ModelLoadException(
                format.rejectionMessage(file.name)
                    ?: Localization.strings.unrecognisedFormat
            )
        }
    }

}
