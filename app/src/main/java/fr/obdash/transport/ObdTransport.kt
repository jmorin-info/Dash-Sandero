package fr.obdash.transport

/** Abstraction du lien serie vers l'adaptateur ELM327 (USB réel ou simulateur). */
interface ObdTransport {
    val name: String
    fun open(): Boolean
    fun write(data: ByteArray)
    /** Retourne le nombre d'octets lus, 0 si rien, -1 si port ferme. */
    fun read(buffer: ByteArray, timeoutMs: Int): Int
    fun close()
}
