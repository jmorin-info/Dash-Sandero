package fr.obdash.elm

import fr.obdash.transport.ObdTransport
import kotlinx.coroutines.delay

/** Pilote ELM327 minimal : commande/réponse jusqu'au prompt '>', init protocole. */
class Elm327(private val transport: ObdTransport) {
    var version: String = "?"
    var protocol: String = "?"

    suspend fun command(cmd: String, timeoutMs: Long = 2000): String {
        transport.write((cmd + "\r").toByteArray())
        val sb = StringBuilder()
        val buf = ByteArray(1024)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val n = transport.read(buf, 100)
            if (n > 0) {
                sb.append(String(buf, 0, n))
                if (sb.contains('>')) break
            } else if (n == 0) {
                delay(5)
            } else {
                break // port ferme
            }
        }
        return sb.toString()
            .replace(">", "")
            .replace('\r', '\n')
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(cmd, true) && !it.startsWith("SEARCHING") }
            .joinToString(" ")
            .trim()
    }

    /** Sequence d'init standard, puis détection auto du protocole via une requête 0100. */
    suspend fun init(): Boolean {
        version = command("ATZ", 5000).ifBlank { "?" }
        command("ATE0")   // echo off
        command("ATL0")   // linefeed off
        command("ATS0")   // espaces off
        command("ATH0")   // headers off
        command("ATSP0")  // protocole auto
        val probe = command("0100", 10000)
        if (!probe.replace(" ", "").contains("4100")) return false
        protocol = command("ATDPN")
        return true
    }

    suspend fun voltage(): Double? =
        command("ATRV").filter { it.isDigit() || it == '.' }.toDoubleOrNull()
}
