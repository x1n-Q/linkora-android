package top.linkora.mobile.project

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.net.URLDecoder

class StaticSiteServer(context: Context) :
    NanoHTTPD("127.0.0.1", ProjectWorkspaceManager.LOCAL_PORT) {
    private val workspace = ProjectWorkspaceManager(context.applicationContext)

    override fun serve(session: IHTTPSession): Response {
        if (session.method != Method.GET && session.method != Method.HEAD) {
            return newFixedLengthResponse(
                Response.Status.METHOD_NOT_ALLOWED,
                MIME_PLAINTEXT,
                "Method not allowed.",
            )
        }

        val decodedPath = runCatching { URLDecoder.decode(session.uri, Charsets.UTF_8.name()) }
            .getOrElse { return badRequest() }
        val requestedFile = when {
            decodedPath == "/" -> "index.html"
            decodedPath.endsWith('/') -> "${decodedPath.trimStart('/')}index.html"
            else -> decodedPath.trimStart('/')
        }
        val file = workspace.openProjectFile(requestedFile)
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found.")

        return try {
            if (session.method == Method.HEAD) {
                file.close()
                newFixedLengthResponse(Response.Status.OK, file.mimeType, "")
            } else if (file.length >= 0) {
                newFixedLengthResponse(Response.Status.OK, file.mimeType, file.input, file.length)
            } else {
                newChunkedResponse(Response.Status.OK, file.mimeType, file.input)
            }.apply {
                addHeader("Cache-Control", "no-store")
                addHeader("X-Content-Type-Options", "nosniff")
            }
        } catch (error: Exception) {
            file.close()
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "The project file could not be read.",
            )
        }
    }

    private fun badRequest(): Response =
        newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid path.")
}
