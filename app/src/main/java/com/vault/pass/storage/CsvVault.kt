package com.vault.pass.storage

import com.vault.pass.VaultEntry

object CsvVault {

    fun export(entries: List<VaultEntry>): String {
        val sb = StringBuilder("name,username,password,notes\n")
        entries.forEach { e ->
            sb.append(esc(e.site)).append(',')
                .append(esc(e.username)).append(',')
                .append(esc(e.password)).append(',')
                .append(esc(e.notes)).append('\n')
        }
        return sb.toString()
    }

    fun import(content: String): List<VaultEntry> {
        val rows = parse(content)
        if (rows.isEmpty()) return emptyList()
        val header = rows[0].map { it.trim().lowercase() }
        val hasHeader = header.any { NAME_COLS.contains(it) || PASS_COLS.contains(it) }

        val idx: (List<String>) -> List<Int> = { cols ->
            listOf(
                cols.indexOfFirst { NAME_COLS.contains(it) }.let { if (it < 0) 0 else it },
                cols.indexOfFirst { USER_COLS.contains(it) }.let { if (it < 0) 1 else it },
                cols.indexOfFirst { PASS_COLS.contains(it) }.let { if (it < 0) 2 else it },
                cols.indexOfFirst { NOTE_COLS.contains(it) }.let { if (it < 0) -1 else it }
            )
        }

        val cols = if (hasHeader) header else emptyList()
        val map = if (hasHeader) idx(cols) else listOf(0, 1, 2, -1)
        val dataRows = if (hasHeader) rows.drop(1) else rows

        val result = mutableListOf<VaultEntry>()
        for (r in dataRows) {
            if (r.size < 3) continue
            val name = r.getOrElse(map[0]) { "" }.trim()
            val user = r.getOrElse(map[1]) { "" }.trim()
            val pass = r.getOrElse(map[2]) { "" }
            if (pass.isBlank()) continue
            val notes = if (map[3] in r.indices) r[map[3]] else ""
            result.add(VaultEntry(site = name, username = user, password = pass, notes = notes.trim()))
        }
        return result
    }

    private val NAME_COLS = listOf("name", "title", "account", "login")
    private val USER_COLS = listOf("username", "login_username", "email", "user", "userid")
    private val PASS_COLS = listOf("password", "login_password", "pwd", "pass")
    private val NOTE_COLS = listOf("notes", "note", "extra")

    private fun esc(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n'))
            "\"" + s.replace("\"", "\"\"") + "\""
        else s

    private fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' -> { row.add(field.toString()); field = StringBuilder() }
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(field.toString()); field = StringBuilder()
                    if (row.any { it.isNotBlank() }) rows.add(row)
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        row.add(field.toString())
        if (row.any { it.isNotBlank() }) rows.add(row)
        return rows
    }
}
