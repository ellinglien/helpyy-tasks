package com.thelightphone.helpytasks

/**
 * A task title split into its leading action verb and everything after it.
 * Ports a small slice of `splitTitleVerb` from `chat-ui/src/lib/task-title.ts`
 * so the phone can bold the verb without pulling in the web app.
 */
data class VerbSplit(val verb: String, val rest: String)

// Deliberately ~35 verbs, not the whole chat-ui list. Weasel leads ("think",
// "look into", "explore", "research", "review", "consider", "decide",
// "figure out") are intentionally absent — no bold is a soft nudge to
// rephrase, same as chat-ui's gtd-lint.
private val ACTION_VERBS = setOf(
    // communication
    "send", "email", "call", "reply", "ask", "text", "contact", "reach", "message",
    // creation
    "draft", "write", "create", "make", "build", "design",
    // submission / admin
    "submit", "apply", "file", "register", "sign", "confirm",
    // completion
    "complete", "finish", "close", "ship",
    // coordination
    "schedule", "book", "meet", "arrange",
    // transactional
    "pay", "buy", "order",
    // share / move
    "share", "upload", "follow",
    // setup / change
    "install", "configure", "set", "fix", "update", "add", "remove", "delete",
    // physical / errand
    "pick", "drop", "get", "take",
    // attention (concrete only)
    "read", "check", "verify", "test",
    // start / do
    "start", "find", "save", "prepare", "clean",
)

// Particles that combine with a verb to form a phrasal verb: "follow up",
// "reach out", "set up".
private val PARTICLES = setOf("up", "out", "in", "off", "on", "back")

private val TRAILING_PUNCTUATION = Regex("[.,;:!?]+$")

private fun normalize(word: String): String =
    word.replace(TRAILING_PUNCTUATION, "").lowercase()

/**
 * If [title]'s first word (or first two, for a phrasal verb) is a common
 * action verb, returns it split from the rest. Returns null otherwise —
 * including for a blank title.
 */
fun splitTitleVerb(title: String): VerbSplit? {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return null

    val words = trimmed.split(Regex("\\s+"))
    val first = normalize(words[0])
    if (first !in ACTION_VERBS) return null

    if (words.size > 1 && normalize(words[1]) in PARTICLES) {
        return VerbSplit(
            verb = "${words[0]} ${words[1]}",
            rest = words.drop(2).joinToString(" "),
        )
    }
    return VerbSplit(verb = words[0], rest = words.drop(1).joinToString(" "))
}
