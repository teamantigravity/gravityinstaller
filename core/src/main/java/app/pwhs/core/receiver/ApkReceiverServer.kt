package app.pwhs.core.receiver

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.File

/**
 * LAN receiver: a phone (scanning the TV's QR) opens the upload page or POSTs an APK here;
 * the file is staged in cache and emitted via [TvReceiverState] for the TV UI to install.
 *
 * The reverse of the mobile sync [ApkHttpServer] (which *serves* files); same NanoHTTPD
 * stack. Uploads must carry the [token] from the QR — a lightweight guard so only a device
 * that scanned this TV's code can push.
 */
class ApkReceiverServer(
    private val context: Context,
    port: Int,
    private val token: String,
) : NanoHTTPD(port) {

    private val stageDir: File = File(context.cacheDir, "received").apply { mkdirs() }

    override fun serve(session: IHTTPSession): Response {
        return when {
            session.method == Method.GET && session.uri == "/" -> uploadPage()
            session.method == Method.GET && session.uri == "/logo.png" -> serveLogo()
            session.method == Method.POST && session.uri == "/upload" -> handleUpload(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun serveLogo(): Response {
        return try {
            val drawable = context.packageManager.getApplicationIcon(context.applicationInfo)
            val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 256
            val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 256
            val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            val bytes = stream.toByteArray()
            newFixedLengthResponse(Response.Status.OK, "image/png", java.io.ByteArrayInputStream(bytes), bytes.size.toLong())
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Logo not found")
        }
    }

    private fun uploadPage(): Response {
        val htmlTemplate = try {
            context.assets.open("upload_page.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "<html><body><h2>Error loading UI</h2><p>${e.message}</p></body></html>"
        }
        val html = htmlTemplate.replace("{{TOKEN}}", token)
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, html)
    }

    private fun handleUpload(session: IHTTPSession): Response {
        if (session.parms["token"] != token && session.parameters["token"]?.firstOrNull() != token) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Bad token")
        }
        // NanoHTTPD writes multipart file parts to temp files; the map gives their paths.
        val files = HashMap<String, String>()
        return try {
            session.parseBody(files)
            val tempPath = files.values.firstOrNull()
                ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "No file")
            val original = session.parameters["apk"]?.firstOrNull()?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() } ?: "received-${System.nanoTime()}.apk"
            val dest = File(stageDir, sanitize(original))
            File(tempPath).copyTo(dest, overwrite = true)
            TvReceiverState.emitReceived(
                ReceivedApk(path = dest.absolutePath, fileName = dest.name, sizeBytes = dest.length())
            )
            newFixedLengthResponse(Response.Status.OK, MIME_HTML, "<h2>Sent ✓ — confirm the install on your TV.</h2>")
        } catch (t: Throwable) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Upload failed: ${t.message}")
        }
    }

    private fun sanitize(name: String): String =
        name.map { if (it.isLetterOrDigit() || it in "-_.") it else '_' }.joinToString("").take(120)
}
