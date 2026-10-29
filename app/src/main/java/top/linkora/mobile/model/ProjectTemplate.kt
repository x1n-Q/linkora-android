package top.linkora.mobile.model

data class ProjectTemplate(
    val id: String,
    val code: String,
    val name: String,
    val description: String,
    val runtimePackage: String?,
    val available: Boolean,
)

val projectTemplates = listOf(
    ProjectTemplate(
        id = "static",
        code = "HTML",
        name = "Static site",
        description = "Serve HTML, CSS, and JavaScript without installing a runtime.",
        runtimePackage = null,
        available = true,
    ),
    ProjectTemplate(
        id = "node",
        code = "NODE",
        name = "Node.js",
        description = "Run JavaScript servers with bundled Node.js 24 LTS and npm.",
        runtimePackage = "Node.js 24 LTS",
        available = true,
    ),
    ProjectTemplate(
        id = "php",
        code = "PHP",
        name = "PHP",
        description = "Run PHP applications and built-in development servers.",
        runtimePackage = "php",
        available = false,
    ),
    ProjectTemplate(
        id = "python",
        code = "PY",
        name = "Python",
        description = "Run Python web projects with a Linkora-managed runtime.",
        runtimePackage = "python",
        available = false,
    ),
)
