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
        name = "HTML website",
        description = "For HTML, CSS, and browser JavaScript. Nothing extra to install.",
        runtimePackage = null,
        available = true,
    ),
    ProjectTemplate(
        id = "node",
        code = "NODE",
        name = "Node.js app",
        description = "For Express, APIs, npm projects, and JavaScript servers.",
        runtimePackage = "Node.js 24 LTS",
        available = true,
    ),
    ProjectTemplate(
        id = "php",
        code = "PHP",
        name = "PHP app",
        description = "For PHP websites and apps that use PHP or PDO SQLite.",
        runtimePackage = "PHP 8.4 Runtime Pack",
        available = true,
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
