package org.oldtvrevival.app

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.text.style.ImageSpan

class EmbeddedWordImageSpan(
    bitmap: Bitmap,
    val imageBytes: ByteArray,
    val imageExtension: String
) : ImageSpan(BitmapDrawable(null, bitmap), ALIGN_BASELINE)
