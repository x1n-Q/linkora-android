package top.linkora.mobile.project

enum class ProjectExecutable(val command: String) {
    Node("node"),
    Npm("npm"),
    Php("php"),
}

data class ProjectCommand(
    val executable: ProjectExecutable,
    val arguments: List<String>,
) {
    fun argumentsFor(host: String, port: Int): List<String> = arguments.map { argument ->
        argument
            .replace("{HOST}", host)
            .replace("{PORT}", port.toString())
    }

    fun display(): String = buildList {
        add(executable.command)
        addAll(arguments.map(::quoteArgument))
    }.joinToString(" ")

    private fun quoteArgument(argument: String): String = if (
        argument.isNotEmpty() && argument.none(Char::isWhitespace) && '"' !in argument
    ) {
        argument
    } else {
        "\"${argument.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }
}

object ProjectCommandParser {
    fun parse(value: String, kind: ProjectKind): ProjectCommand {
        val tokens = tokenize(value.trim())
        require(tokens.isNotEmpty()) { "Enter a start command." }
        val executable = ProjectExecutable.entries.firstOrNull {
            it.command == tokens.first().lowercase()
        } ?: throw IllegalArgumentException("Use node, npm, or php as the command.")

        when (kind) {
            ProjectKind.Node -> require(executable in setOf(ProjectExecutable.Node, ProjectExecutable.Npm)) {
                "Node.js projects can start with node or npm."
            }
            ProjectKind.Php -> require(executable == ProjectExecutable.Php) {
                "PHP projects must start with php."
            }
            ProjectKind.Static -> throw IllegalArgumentException(
                "Static projects do not use a start command.",
            )
        }

        require(tokens.drop(1).none { '\u0000' in it || '\n' in it || '\r' in it }) {
            "The command contains unsupported control characters."
        }
        return ProjectCommand(executable, tokens.drop(1))
    }

    internal fun tokenize(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaping = false

        fun finishToken() {
            if (current.isNotEmpty()) {
                tokens += current.toString()
                current.clear()
            }
        }

        value.forEach { character ->
            when {
                escaping -> {
                    current.append(character)
                    escaping = false
                }
                character == '\\' -> escaping = true
                quote != null && character == quote -> quote = null
                quote != null -> current.append(character)
                character == '\'' || character == '"' -> quote = character
                character.isWhitespace() -> finishToken()
                else -> current.append(character)
            }
        }
        require(!escaping) { "The command ends with an incomplete escape." }
        require(quote == null) { "The command contains an unclosed quote." }
        finishToken()
        return tokens
    }
}
