package app.pwhs.tv.presentation.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.pwhs.tv.MainActivity
import app.pwhs.tv.util.LocaleHelper
import java.util.Locale

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val currentTag = remember { LocaleHelper.getStoredLanguage(context) }
    
    val languages = remember {
        listOf(
            "" to "System Default",
            "en" to "English",
            "vi" to "Tiếng Việt",
            "zh" to "中文",
            "es" to "Español",
            "pt" to "Português",
            "ru" to "Русский",
            "hi" to "हिन्दी",
            "ar" to "العربية",
            "tr" to "Türkçe"
        ).sortedBy { if (it.first.isEmpty()) "" else it.second }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp),
        contentPadding = PaddingValues(top = 32.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "Language",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        items(languages) { (tag, name) ->
            val isSelected = currentTag == tag
            val shape = RoundedCornerShape(12.dp)
            Surface(
                onClick = {
                    if (!isSelected) {
                        LocaleHelper.setAppLanguage(context, tag)
                        val intent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                shape = ClickableSurfaceDefaults.shape(shape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    focusedContainerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    focusedContentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    }
}
