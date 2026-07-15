package fr.obdash.transport

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

/**
 * Liaison USB OTG vers le vLinker FS USB (pont FTDI, 115200 8N1 par défaut).
 * La lib usb-serial-for-android gere FTDI/CH34x/CP210x/CDC sans root.
 */
class UsbTransport(private val context: Context, private val baud: Int = 115200) : ObdTransport {
    override val name = "vLinker FS (USB)"
    private var port: UsbSerialPort? = null
    private var permissionAsked = false

    override fun open(): Boolean {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(manager).firstOrNull() ?: return false
        if (!manager.hasPermission(driver.device)) {
            requestPermission(manager, driver.device)
            return false
        }
        val connection = manager.openDevice(driver.device) ?: return false
        return try {
            val p = driver.ports[0]
            p.open(connection)
            p.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            runCatching { p.dtr = true; p.rts = true }
            port = p
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun requestPermission(manager: UsbManager, device: UsbDevice) {
        if (permissionAsked) return
        permissionAsked = true
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(
            context, 0,
            Intent("fr.obdash.USB_PERMISSION").setPackage(context.packageName),
            flags
        )
        manager.requestPermission(device, pi)
    }

    override fun write(data: ByteArray) {
        try { port?.write(data, 500) } catch (_: Exception) {}
    }

    override fun read(buffer: ByteArray, timeoutMs: Int): Int =
        try { port?.read(buffer, timeoutMs) ?: -1 } catch (e: Exception) { -1 }

    override fun close() {
        runCatching { port?.close() }
        port = null
    }
}
