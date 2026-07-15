package fr.obdash.transport

import kotlin.math.max
import kotlin.math.sin

/**
 * Faux ECU pour developper sans voiture : répond aux commandes AT, aux PIDs mode 01,
 * et desormais a l'UDS (session, temps d'injection, quelques paramètres Renault) et aux
 * commandes d'actionneur, afin que tous les onglets soient demontrables en simulation.
 */
class SimTransport : ObdTransport {
    override val name = "Simulation"
    private val out = StringBuilder()
    private val t0 = System.currentTimeMillis()
    private fun t() = (System.currentTimeMillis() - t0) / 1000.0

    private fun speed(): Int = max(0.0, 55.0 + 48.0 * sin(t() / 14.0)).toInt()
    private fun rpm(): Int = 880 + speed() * 36
    private fun mapKpa(): Int = (30 + 55 * ((sin(t() / 14.0) + 1) / 2)).toInt().coerceIn(25, 100)
    private fun coolant(): Int = (18 + t() * 1.5).toInt().coerceAtMost(91)
    private fun throttle(): Int = (14 + 12 * ((sin(t() / 14.0) + 1) / 2)).toInt()
    private fun tiMs(): Double = 2.0 + mapKpa() / 45.0            // ~2.5-4.2 ms selon charge

    private fun b1(v: Int) = String.format("%02X", v and 0xFF)
    private fun b2(v: Int) = String.format("%04X", v and 0xFFFF)
    private fun b3(v: Int) = String.format("%06X", v and 0xFFFFFF)

    /** Donnees (hex, sans prefixe 41+pid) d'un PID mode 01 ; null si non fourni en simu. */
    private fun pidData(p: Int): String? = when (p) {
        0x0C -> b2(rpm() * 4)
        0x0D -> b1(speed())
        0x0B -> b1(mapKpa())
        0x05 -> b1(coolant() + 40)
        0x0F -> b1(24 + 40)
        0x11 -> b1(throttle() * 255 / 100)
        0x04 -> b1((mapKpa() * 2).coerceAtMost(255))
        0x06 -> b1(131)                    // STFT +2%
        0x07 -> b1(126)                    // LTFT -1.5%
        0x0E -> b1((10 + 64) * 2)          // avance ~10 deg
        0x1F -> b2(t().toInt())            // uptime
        0x21 -> b2(0)                      // distance MIL
        0x2F -> b1(62 * 255 / 100)
        0x33 -> b1(100)
        0x42 -> b2(14100)                  // tension calculateur 14.1V
        0x46 -> b1(22 + 40)                // ambiant 22C
        else -> null                       // 0x5E non fourni -> speed-density
    }

    override fun open() = true
    override fun close() {}

    override fun write(data: ByteArray) {
        val cmd = String(data).trim().uppercase().replace(" ", "")
        val resp = when {
            // --- AT
            cmd.startsWith("ATZ") -> "ELM327 v2.2 (SIM)"
            cmd.startsWith("ATRV") -> "14.1V"
            cmd.startsWith("ATDPN") -> "A6"
            cmd.startsWith("AT") -> "OK"

            // --- OBD mode 01
            cmd == "0100" -> "4100BE3FA813"
            cmd == "0120" -> "412090000000"
            cmd == "0140" -> "414044000000"              // supporte 0x42 (tension) et 0x46 (ambiant)
            cmd == "0101" -> "410100072100"              // MIL off, moniteurs (catalyseur/O2 prets)
            // Mode 01 (mono ou MULTI-PID) : "010C0D0B11..." renvoie tous les PID d'un coup
            cmd.startsWith("01") && cmd.length >= 4 -> {
                val pids = cmd.substring(2).chunked(2).mapNotNull { it.toIntOrNull(16) }
                val sb = StringBuilder("41")
                for (p in pids) pidData(p)?.let { sb.append(String.format("%02X", p)).append(it) }
                if (sb.length > 2) sb.toString() else "NO DATA"
            }
            cmd == "03" -> "43010113"                    // P0113 simule
            cmd == "07" -> "4700"
            cmd == "04" -> "44"

            // --- Mode 02 (freeze frame) : figé au moment du P0113
            cmd.startsWith("0202") -> "4202000113"
            cmd.startsWith("020C") -> "420C00" + b2(rpm() * 4)
            cmd.startsWith("020D") -> "420D00" + b1(speed())
            cmd.startsWith("0204") -> "420400" + b1((mapKpa() * 2).coerceAtMost(255))
            cmd.startsWith("0205") -> "420500" + b1(coolant() + 40)
            cmd.startsWith("020F") -> "420F00" + b1(24 + 40)
            cmd.startsWith("020B") -> "420B00" + b1(mapKpa())
            cmd.startsWith("0211") -> "421100" + b1(throttle() * 255 / 100)

            // --- UDS (sessions / tester-present)
            cmd == "10C0" -> "50C0003201F4"
            cmd == "1003" -> "5003003201F4"
            cmd == "1001" -> "5001003201F4"
            cmd.startsWith("10") -> "50" + cmd.substring(2, 4.coerceAtMost(cmd.length))
            cmd == "3E00" -> "7E00"

            // --- UDS ReadDataByIdentifier (paramètres Renault)
            cmd == "222854" || cmd == "222855" || cmd == "222856" ->
                "62" + cmd.substring(2) + b3((tiMs() / 0.004).toInt())
            cmd == "222002" -> "622002" + b2(rpm() * 4)
            cmd == "222003" -> "622003" + b2(speed() * 100)
            cmd == "222004" -> "622004" + b2(((80 + 400) / 0.03125).toInt())
            cmd == "222001" -> "622001" + b2(((coolant() + 273) / 0.1).toInt())
            cmd.startsWith("2228") || cmd.startsWith("2229") || cmd.startsWith("222A") ->
                "62" + cmd.substring(2) + "0000"
            cmd.startsWith("22F1") -> "62" + cmd.substring(2) + "53494D"   // "SIM" ASCII
            // Chassis ABS : 4 roues = vitesse vehicule +/- petit ecart realiste (x0.01 km/h)
            cmd == "224B00" -> "624B00" + b2(((speed() + 0.12) * 100).toInt())
            cmd == "224B01" -> "624B01" + b2(((speed() - 0.09) * 100).toInt())
            cmd == "224B02" -> "624B02" + b2(((speed() + 0.31) * 100).toInt())
            cmd == "224B03" -> "624B03" + b2(((speed() - 0.22) * 100).toInt())
            // Angle volant : oscillation lente +/-28 deg (x0.1, offset -3276.7)
            cmd == "220100" -> "620100" + b2(((28.0 * sin(t() / 9.0) + 3276.7) * 10).toInt())
            // Odometre cluster (3 octets, km)
            cmd == "220207" -> "620207" + b3(84512)
            // Couple modelise (DID 2037) : suit la charge, echelle 0.03125 Nm offset -400
            cmd == "222037" -> {
                val nm = 8.0 + mapKpa() * 0.8 + throttle() * 0.3        // ~10..95 Nm plausible
                "622037" + b2(((nm + 400.0) / 0.03125).toInt())
            }
            cmd.startsWith("22") -> "62" + cmd.substring(2) + "0000"

            // --- UDS ReadDTCInformation
            cmd.startsWith("1902") -> "5902FF"
            cmd.startsWith("19") -> "5900"

            // --- actionneurs (KWP 30 / UDS 2F) : réponse positive
            cmd.startsWith("30") -> "70" + cmd.substring(2, 4.coerceAtMost(cmd.length)) + "00"
            cmd.startsWith("2F") -> "6F" + cmd.substring(2, 6.coerceAtMost(cmd.length)) + "00"

            else -> "NO DATA"
        }
        out.append(resp).append("\r>")
    }

    override fun read(buffer: ByteArray, timeoutMs: Int): Int {
        if (out.isEmpty()) { Thread.sleep(10); return 0 }
        val bytes = out.toString().toByteArray()
        val n = minOf(bytes.size, buffer.size)
        System.arraycopy(bytes, 0, buffer, 0, n)
        out.delete(0, n)
        return n
    }
}
