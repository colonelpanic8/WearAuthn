package me.henneke.wearauthn.ui.components

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** A string resource with its `<b>` markup kept. */
@Composable
fun styledStringResource(@StringRes id: Int): AnnotatedString {
    val resources = LocalContext.current.resources
    return remember(resources, id) {
        val text = resources.getText(id)
        buildAnnotatedString {
            append(text.toString())
            if (text is Spanned) {
                for (span in text.getSpans(0, text.length, StyleSpan::class.java)) {
                    if (span.style and Typeface.BOLD != 0) {
                        addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold),
                            text.getSpanStart(span),
                            text.getSpanEnd(span),
                        )
                    }
                }
            }
        }
    }
}

/** [template] with its single `%1$s` replaced by [argument] in bold. */
fun boldArgument(template: String, argument: String): AnnotatedString {
    val parts = template.split("%1\$s", limit = 2)
    return buildAnnotatedString {
        append(parts[0])
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(argument) }
        if (parts.size > 1) append(parts[1])
    }
}

/** A host name that wraps after its dots rather than in the middle of a label. */
fun breakableHost(host: String): String = host.replace(".", ".\u200B")
