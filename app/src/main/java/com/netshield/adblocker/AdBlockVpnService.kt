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

<<<<<<< HEAD
/** DNS-only VPN. It intentionally does not claim to proxy arbitrary TCP/HTTPS traffic. */
class AdBlockVpnService : VpnService() {
=======
class AdBlockVpnService : VpnService() {

>>>>>>> 1b20695d07943a1dacf8182534f4aa60ccff61d4
    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var isRunning = false
    private var workerThread: Thread? = null

    companion object {
        const val ACTION_START = "com.netshield.adblocker.START"
        const val ACTION_STOP = "com.netshield.adblocker.STOP"
        private const val CHANNEL_ID = "netshield_vpn_channel"
<<<<<<< HEAD
        @Volatile var active = false
            private set
=======
        var active = false
>>>>>>> 1b20695d07943a1dacf8182534f4aa60ccff61d4
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        startForegroundNotification()
        startVpn()
<<<<<<< HEAD
        return if (isRunning) START_STICKY else START_NOT_STICKY
=======
        return START_STICKY
>>>>>>> 1b20695d07943a1dacf8182534f4aa60ccff61d4
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
<<<<<<< HEAD
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
=======
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NetShield AdBlocker VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NetShield AdBlocker Active")
            .setContentText("Filtering ads, trackers & popups system-wide")
>>>>>>> 1b20695d07943a1dacf8182534f4aa60ccff61d4
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
<<<<<<< HEAD
        startForeground(101, notification)
    }

    @Synchronized
    private fun startVpn() {
        if (isRunning) return
        BlocklistManager.initialize(applicationContext)
        val established = Builder()
            .setSession("NetShield DNS protection")
            .setMtu(1500)
=======

        startForeground(101, notification)
    }

    private fun startVpn() {
        if (isRunning) return
        val builder = Builder()
            .setSession("NetShield AdBlocker")
>>>>>>> 1b20695d07943a1dacf8182534f4aa60ccff61d4
            .addAddress("10.111.222.1", 32)
            .addDnsServer("10.111.222.2")
            .addRoute("10.111.222.2", 32)
            .setBlocking(true)
<<<<<<< HEAD
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
=======

        vpnInterface = builder.establish() ?: return
        isRunning = true
        active = true

        workerThread = Thread {
            val fd = vpnInterface ?: return@Thread
            val input = FileInputStream(fd.fileDescriptor)
            val output = FileOutputStream(fd.fileDescriptor)
            val packet = ByteArray(32767)

            while (isRunning) {
                try {
                    val length = input.read(packet)
                    if (length > 0) {
                        handleIpPacket(packet, length, output)
                    }
                } catch (_: Exception) {
                    if (!isRunning) break
                }
            }
        }.apply { start() }
    }

    private fun handleIpPacket(packet: ByteArray, length: Int, output: FileOutputStream) {
        if (length < 28) return
        val version = (packet[0].toInt() shr 4) and 0xF
        if (version != 4) return
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return

        val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
        if (length < ipHeaderLen + 8) return

        val dstPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or
                (packet[ipHeaderLen + 3].toInt() and 0xFF)
        if (dstPort != 53) return

        val dnsOffset = ipHeaderLen + 8
        val dnsPayloadLen = length - dnsOffset
        if (dnsPayloadLen <= 12) return

        val dnsPayload = packet.copyOfRange(dnsOffset, length)
        val queriedDomain = extractDnsQueryDomain(dnsPayload)

        val dnsResponse: ByteArray = if (queriedDomain != null && BlocklistManager.isBlocked(queriedDomain)) {
            BlocklistManager.blockedCount.incrementAndGet()
            buildBlockedDnsResponse(dnsPayload)
        } else {
            forwardDnsToUpstream(dnsPayload) ?: return
        }

        val responseIpPacket = buildUdpIpResponse(packet, ipHeaderLen, dnsResponse)
        synchronized(output) {
            output.write(responseIpPacket)
        }
    }

    private fun extractDnsQueryDomain(dns: ByteArray): String? {
        return try {
            var pos = 12
            val labels = mutableListOf<String>()
            while (pos < dns.size) {
                val len = dns[pos].toInt() and 0xFF
                if (len == 0) break
                if (pos + 1 + len > dns.size) return null
                labels.add(String(dns, pos + 1, len, Charsets.UTF_8))
                pos += 1 + len
            }
            if (labels.isEmpty()) null else labels.joinToString(".")
        } catch (_: Exception) {
            null
        }
    }

    private fun buildBlockedDnsResponse(query: ByteArray): ByteArray {
        val resp = query.copyOf()
        resp[2] = (0x81).toByte()
        resp[3] = (0x83).toByte()
        return resp
>>>>>>> 1b20695d07943a1dacf8182534f4aa60ccff61d4
    }

    private fun forwardDnsToUpstream(query: ByteArray): ByteArray? {
        return try {
<<<<<<< HEAD
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
=======
            val socket = DatagramSocket()
            protect(socket)
            socket.soTimeout = 3000
            val upstream = InetAddress.getByName("1.1.1.1")
            socket.send(DatagramPacket(query, query.size, upstream, 53))
            val buf = ByteArray(1024)
            val reply = DatagramPacket(buf, buf.size)
            socket.receive(reply)
            socket.close()
            buf.copyOf(reply.length)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildUdpIpResponse(origPacket: ByteArray, ipHeaderLen: Int, udpPayload: ByteArray): ByteArray {
        val totalLen = 20 + 8 + udpPayload.size
        val out = ByteArray(totalLen)
        val buf = ByteBuffer.wrap(out)

        buf.put(0x45.toByte())
        buf.put(0x00.toByte())
        buf.putShort(totalLen.toShort())
        buf.putShort(0)
        buf.putShort(0x4000.toShort())
        buf.put(64.toByte())
        buf.put(17.toByte())
        buf.putShort(0)
        buf.put(origPacket, 16, 4)
        buf.put(origPacket, 12, 4)

        var sum = 0
        for (i in 0 until 20 step 2) {
            sum += ((out[i].toInt() and 0xFF) shl 8) or (out[i + 1].toInt() and 0xFF)
        }
        while ((sum shr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        buf.putShort(10, sum.inv().toShort())

        buf.position(20)
        buf.put(origPacket, ipHeaderLen + 2, 2)
        buf.put(origPacket, ipHeaderLen, 2)
        buf.putShort((8 + udpPayload.size).toShort())
        buf.putShort(0)
        buf.put(udpPayload)

        return out
    }

    private fun stopVpn() {
        isRunning = false
        active = false
        workerThread?.interrupt()
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
>>>>>>> 1b20695d07943a1dacf8182534f4aa60ccff61d4
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
