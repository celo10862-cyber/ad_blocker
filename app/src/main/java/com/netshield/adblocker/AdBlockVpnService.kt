package com.netshield.adblocker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

/** DNS-only VPN. It intentionally does not claim to proxy arbitrary TCP/HTTPS traffic. */
class AdBlockVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var isRunning = false
    private var workerThread: Thread? = null

    companion object {
        const val ACTION_START = "com.netshield.adblocker.START"
        const val ACTION_STOP = "com.netshield.adblocker.STOP"
        private const val CHANNEL_ID = "netshield_vpn_channel"
        @Volatile var active = false
            private set
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        startForegroundNotification()
        startVpn()
        return if (isRunning) START_STICKY else START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "NetShield DNS protection", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NetShield protection is active")
            .setContentText("Filtering DNS requests on this device")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
        startForeground(101, notification)
    }

    @Synchronized
    private fun startVpn() {
        if (isRunning) return
        BlocklistManager.initialize(applicationContext)
        val established = Builder()
            .setSession("NetShield DNS protection")
            .setMtu(1500)
            .addAddress("10.111.222.1", 32)
            .addDnsServer("10.111.222.2")
            .addRoute("10.111.222.2", 32)
            .setBlocking(true)
            .establish()
        if (established == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        vpnInterface = established
        isRunning = true
        active = true
        workerThread = Thread({ packetLoop(established) }, "NetShield-DNS-VPN").apply {
            isDaemon = true
            start()
        }
    }

    private fun packetLoop(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val packet = ByteArray(32767)
        try {
            while (isRunning && !Thread.currentThread().isInterrupted) {
                val length = input.read(packet)
                if (length > 0) handleIpPacket(packet.copyOf(length), output)
            }
        } catch (_: Exception) {
            // Closing the VPN descriptor unblocks the reader during normal shutdown.
        }
    }

    private fun handleIpPacket(packet: ByteArray, output: FileOutputStream) {
        if (packet.size < 28) return
        val version = (packet[0].toInt() ushr 4) and 0x0f
        if (version != 4 || (packet[9].toInt() and 0xff) != 17) return // IPv4 UDP only
        val ipHeaderLength = (packet[0].toInt() and 0x0f) * 4
        if (ipHeaderLength < 20 || packet.size < ipHeaderLength + 8) return
        val fragmentField = u16(packet, 6)
        if ((fragmentField and 0x3fff) != 0) return // DNS fragments are not reassembled
        if (!(packet[16] == 10.toByte() && packet[17] == 111.toByte() &&
                    packet[18] == 222.toByte() && packet[19] == 2.toByte())) return

        val ipTotalLength = u16(packet, 2)
        if (ipTotalLength > packet.size || ipTotalLength < ipHeaderLength + 8) return
        val udpLength = u16(packet, ipHeaderLength + 4)
        if (udpLength < 20 || ipHeaderLength + udpLength > ipTotalLength) return
        val sourcePort = u16(packet, ipHeaderLength)
        val destinationPort = u16(packet, ipHeaderLength + 2)
        if (destinationPort != 53) return
        val dns = packet.copyOfRange(ipHeaderLength + 8, ipHeaderLength + udpLength)
        if (dns.size < 17 || (dns[2].toInt() and 0x80) != 0 || u16(dns, 4) < 1) return

        val domain = extractDnsQuestion(dns) ?: return
        val response = if (BlocklistManager.isBlocked(domain)) {
            BlocklistManager.blockedCount.incrementAndGet()
            buildNxDomainResponse(dns)
        } else {
            forwardDnsToUpstream(dns) ?: return
        }
        val responsePacket = buildUdpIpResponse(packet, ipHeaderLength, sourcePort, response)
        synchronized(output) { output.write(responsePacket) }
    }

    private fun extractDnsQuestion(dns: ByteArray): String? {
        return try {
        var pos = 12
        val labels = ArrayList<String>()
        while (pos < dns.size) {
            val size = dns[pos++].toInt() and 0xff
            if (size == 0) break
            if (size > 63 || pos + size > dns.size) return null
            val label = String(dns, pos, size, Charsets.US_ASCII)
            if (!label.matches(Regex("[A-Za-z0-9_-]+"))) return null
            labels += label
            pos += size
        }
        if (labels.isEmpty() || pos + 4 > dns.size) null else labels.joinToString(".")
        } catch (_: Exception) { null }
    }

    private fun buildNxDomainResponse(query: ByteArray): ByteArray {
        val response = query.copyOf()
        response[2] = ((response[2].toInt() and 0x01) or 0x80).toByte() // QR and preserve RD
        response[3] = 0x83.toByte() // RA and NXDOMAIN
        response[6] = 0; response[7] = 0 // no answer records
        response[8] = 0; response[9] = 0
        response[10] = 0; response[11] = 0
        return response
    }

    private fun forwardDnsToUpstream(query: ByteArray): ByteArray? {
        return try {
        DatagramSocket().use { socket ->
            if (!protect(socket)) return null
            socket.soTimeout = 3500
            val server = InetAddress.getByName("1.1.1.1")
            socket.send(DatagramPacket(query, query.size, server, 53))
            val buffer = ByteArray(4096)
            val reply = DatagramPacket(buffer, buffer.size)
            socket.receive(reply)
            buffer.copyOf(reply.length)
        }
        } catch (_: Exception) { null }
    }

    private fun buildUdpIpResponse(original: ByteArray, ipHeaderLength: Int, originalSourcePort: Int, dns: ByteArray): ByteArray {
        val totalLength = 20 + 8 + dns.size
        val result = ByteArray(totalLength)
        val out = ByteBuffer.wrap(result)
        out.put(0x45.toByte()); out.put(0)
        out.putShort(totalLength.toShort())
        out.putShort(0); out.putShort(0x4000.toShort())
        out.put(64.toByte()); out.put(17.toByte()); out.putShort(0)
        out.put(original, 16, 4) // source = VPN DNS destination
        out.put(original, 12, 4) // destination = original client
        var checksum = 0
        for (i in 0 until 20 step 2) checksum += u16(result, i)
        while ((checksum ushr 16) != 0) checksum = (checksum and 0xffff) + (checksum ushr 16)
        out.putShort(10, checksum.inv().toShort())
        out.position(20)
        out.putShort(53.toShort())
        out.putShort(originalSourcePort.toShort())
        out.putShort((8 + dns.size).toShort())
        out.putShort(0) // UDP checksum is optional for IPv4
        out.put(dns)
        return result
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    @Synchronized
    private fun stopVpn() {
        isRunning = false
        active = false
        try { vpnInterface?.close() } catch (_: Exception) { }
        vpnInterface = null
        workerThread?.interrupt()
        workerThread = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
