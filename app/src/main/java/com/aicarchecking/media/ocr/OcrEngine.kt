package com.aicarchecking.media.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** OCR abstraction so the engine can be swapped (e.g. for a cloud OCR) without touching callers. */
interface OcrEngine {
    /** Returns recognised text, or throws [OcrException] with a user-friendly message. */
    suspend fun recognize(bitmap: Bitmap): String
}

class OcrException(message: String) : Exception(message)

/** On-device ML Kit Latin text recognition. Works offline; images never leave the phone. */
class MlKitOcrEngine : OcrEngine {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognize(bitmap: Bitmap): String = try {
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
    } catch (e: Exception) {
        throw OcrException("We couldn't read text from this image. Try a sharper, closer photo without glare.")
    }
}
