package top.linkora.mobile.project

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.net.URLDecoder
import top.linkora.mobile.settings.HostingSettings

class StaticSiteServer(context: Context) :
    NanoHTTPD(HostingSettings(context).read().bindHost, ProjectWorkspaceManager.LOCAL_PORT) {
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
        val acceptsHtml = session.headers["accept"]
            ?.contains("text/html", ignoreCase = true) == true
        val extensionlessPath = requestedFile.substringAfterLast('/').let { '.' !in it }
        val resolved = workspace.openProjectFile(requestedFile)?.let {
            ResolvedProjectFile(it, Response.Status.OK)
        } ?: if (extensionlessPath) {
            workspace.openProjectFile("$requestedFile/index.html")?.let {
                ResolvedProjectFile(it, Response.Status.OK)
            }
        } else {
            null
        } ?: if (acceptsHtml && extensionlessPath) {
            workspace.openProjectFile("index.html")?.let {
                ResolvedProjectFile(it, Response.Status.OK)
            }
        } else {
            null
        } ?: workspace.openProjectFile("404.html")?.let {
            ResolvedProjectFile(it, Response.Status.NOT_FOUND)
        } ?: return newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            MIME_PLAINTEXT,
            "Not found.",
        )

        return try {
            if (session.method == Method.HEAD) {
                resolved.file.close()
                newFixedLengthResponse(resolved.status, resolved.file.mimeType, "")
            } else if (resolved.file.length >= 0) {
                newFixedLengthResponse(
                    resolved.status,
                    resolved.file.mimeType,
                    resolved.file.input,
                    resolved.file.length,
                )
            } else {
                newChunkedResponse(
                    resolved.status,
                    resolved.file.mimeType,
                    resolved.file.input,
                )
            }.apply {
                addHeader("Cache-Control", "no-store")
                addHeader("X-Content-Type-Options", "nosniff")
                addHeader("Referrer-Policy", "strict-origin-when-cross-origin")
            }
        } catch (error: Exception) {
            resolved.file.close()
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "The project file could not be read.",
            )
        }
    }

    private fun badRequest(): Response =
        newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid path.")

    private data class ResolvedProjectFile(
        val file: ProjectFile,
        val status: Response.Status,
    )
}
