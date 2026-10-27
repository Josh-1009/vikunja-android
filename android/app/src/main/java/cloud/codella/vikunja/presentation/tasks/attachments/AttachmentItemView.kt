package cloud.codella.vikunja.presentation.tasks.attachments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cloud.codella.vikunja.core.model.Attachment

@Composable
fun AttachmentItemView(
    attachment: Attachment,
    previewUrl: String? = null,
    onViewClick: (Attachment) -> Unit,
    onDeleteClick: ((Attachment) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val extension = attachment.name.substringAfterLast('.', "").uppercase()
    val isImage = extension in listOf("JPG", "JPEG", "PNG", "WEBP", "GIF", "BMP")
    val formattedSize = formatFileSize(attachment.size)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onViewClick(attachment) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail or Document Type Icon
            if (isImage && !previewUrl.isNullOrBlank()) {
                AsyncImage(
                    model = previewUrl,
                    contentDescription = attachment.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = getExtensionColor(extension).copy(alpha = 0.15f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = getExtensionIcon(extension),
                            contentDescription = extension,
                            tint = getExtensionColor(extension),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // File Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // File Size
                    Text(
                        text = formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Extension / MIME Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = if (extension.isNotBlank()) extension.take(4) else "FILE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Action Buttons
            IconButton(onClick = { onViewClick(attachment) }) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "View file",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (onDeleteClick != null) {
                IconButton(onClick = { onDeleteClick(attachment) }) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete attachment",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes.toDouble() / (1024 * 1024))
        bytes >= 1024 -> "${bytes / 1024} KB"
        bytes > 0 -> "$bytes B"
        else -> "0 KB"
    }
}

@Composable
private fun getExtensionColor(ext: String) = when (ext) {
    "PDF" -> MaterialTheme.colorScheme.error
    "JPG", "JPEG", "PNG", "WEBP", "GIF" -> MaterialTheme.colorScheme.primary
    "ZIP", "RAR", "TAR", "7Z" -> MaterialTheme.colorScheme.tertiary
    "DOC", "DOCX", "TXT", "MD" -> MaterialTheme.colorScheme.secondary
    "MP3", "WAV", "OGG", "M4A" -> MaterialTheme.colorScheme.primary
    "MP4", "MKV", "MOV" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun getExtensionIcon(ext: String): ImageVector = when (ext) {
    "PDF" -> Icons.Default.PictureAsPdf
    "JPG", "JPEG", "PNG", "WEBP", "GIF", "BMP" -> Icons.Default.Image
    "ZIP", "RAR", "TAR", "7Z" -> Icons.Default.FolderZip
    "MP3", "WAV", "OGG", "M4A" -> Icons.Default.Audiotrack
    "MP4", "MKV", "MOV", "AVI" -> Icons.Default.VideoFile
    "DOC", "DOCX", "TXT", "MD", "RTF" -> Icons.Default.Description
    else -> Icons.Default.AttachFile
}
