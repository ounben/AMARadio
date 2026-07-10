package com.ounben.amaradio.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ounben.amaradio.R

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = stringResource(R.string.version_name)
    val gitHash = stringResource(R.string.git_hash)
    val buildDate = stringResource(R.string.build_date)
    
    val fullVersion = if (gitHash.isNotEmpty()) {
        "$version (git $gitHash) $buildDate"
    } else {
        "$version $buildDate"
    }

    val aboutText = stringResource(R.string.about_text)
    
    val annotatedString = buildAnnotatedString {
        append(aboutText)
        
        // Find URLs
        val urlRegex = "(https?://[\\w-]+(\\.[\\w-]+)+(/\\S*)?)".toRegex()
        urlRegex.findAll(aboutText).forEach { result ->
            addStyle(
                style = SpanStyle(
                    color = AmaradioAmber,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Bold
                ),
                start = result.range.first,
                end = result.range.last + 1
            )
            addStringAnnotation(
                tag = "URL",
                annotation = result.value,
                start = result.range.first,
                end = result.range.last + 1
            )
        }
        
        // Find Emails
        val emailRegex = "([a-zA-Z0-9._-]+@[a-zA-Z0-9._-]+\\.[a-zA-Z0-9_-]+)".toRegex()
        emailRegex.findAll(aboutText).forEach { result ->
            addStyle(
                style = SpanStyle(
                    color = AmaradioAmber,
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Bold
                ),
                start = result.range.first,
                end = result.range.last + 1
            )
            addStringAnnotation(
                tag = "EMAIL",
                annotation = result.value,
                start = result.range.first,
                end = result.range.last + 1
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Internal Back Button Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.accessibility_back))
            }
            Text(
                text = stringResource(R.string.settings_about),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = AmaradioAmber,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.ad_free_promise),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.about_version, fullVersion),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            SelectionContainer {
                @Suppress("DEPRECATION")
                ClickableText(
                    text = annotatedString,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textAlign = TextAlign.Start,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    onClick = { offset ->
                        annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                try {
                                    uriHandler.openUri(annotation.item)
                                } catch (_: Exception) {}
                            }
                        
                        annotatedString.getStringAnnotations(tag = "EMAIL", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:${annotation.item}")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                    }
                )
            }
        }
    }
}
