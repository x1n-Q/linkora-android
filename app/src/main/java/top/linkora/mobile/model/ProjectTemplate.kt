package top.linkora.mobile.model

data class ProjectTemplate(
    val id: String,
    val code: String,
    val name: String,
    val description: String,
    val runtimePackage: String?,
)

val projectTemplates = listOf(
    ProjectTemplate(
        id = "static",
        code = "HTML",
        name = "Static site",
        description = "Serve HTML, CSS, and JavaScript without installing a runtime.",
        runtimePackage = null,
    ),
    ProjectTemplate(
        id = "node",
        code = "NODE",
        name = "Node.js",
        description = "Run npm projects with a Termux-managed Node.js runtime.",
        runtimePackage = "nodejs-lts",
    ),
    ProjectTemplate(
        id = "php",
        code = "PHP",
        name = "PHP",
        description = "Run PHP applications and built-in development servers.",
        runtimePackage = "php",
    ),
    ProjectTemplate(
        id = "python",
        code = "PY",
        name = "Python",
        description = "Run Python web projects from an isolated Termux workspace.",
        runtimePackage = "python",
    ),
)
