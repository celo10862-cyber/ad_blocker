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

class AdBlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var isRunning = false
    private var workerThread: Thread? = null

    companion object {
        const val ACTION_START = "com.netshield.adblocker.START"
        const val ACTION_STOP = "com.netshield.adblocker.STOP"
        private const val CHANNEL_ID = "netshield_vpn_channel"
        @Volatile var active = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        startForegroundNotification()
        startVpn()
        return START_STICKY
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
            .setContentText("Filtering supported DNS requests")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()

        startForeground(101, notification)
    }

    private fun startVpn() {
        if (isRunning) return
        val builder = Builder()
            .setSession("NetShield AdBlocker")
            .addAddress("10.111.222.1", 32)
            .addDnsServer("10.111.222.2")
            .addRoute("10.111.222.2", 32)
            .addAddress("fd00:1111:2222::1", 128)
            .addDnsServer("fd00:1111:2222::2")
            .addRoute("fd00:1111:2222::2", 128)
            .setBlocking(true)

        // An empty selection means all visible apps. Otherwise only selected apps
        // use this VPN; Android's per-app VPN routing is enforced by the platform.
        if (BlocklistManager.allowedPackages.isNotEmpty()) {
            BlocklistManager.allowedPackages.forEach { packageName ->
                runCatching { builder.addAllowedApplication(packageName) }
            }
        }

        vpnInterface = builder.establish()
        if (vpnInterface == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
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
        if (version == 6) {
            handleIpv6DnsPacket(packet, length, output)
            return
        }
        if (version != 4) return
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return

        // Fragmented UDP datagrams need reassembly; do not misparse them as DNS.
        val fragmentField = ((packet[6].toInt() and 0xFF) shl 8) or (packet[7].toInt() and 0xFF)
        if ((fragmentField and 0x3FFF) != 0) return

        val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
        if (length < ipHeaderLen + 8) return

        if (ipHeaderLen < 20 || length < ipHeaderLen + 8) return
        if (!(packet[16] == 10.toByte() && packet[17] == 111.toByte() &&
                    packet[18] == 222.toByte() && packet[19] == 2.toByte())) return
        val dstPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or
                (packet[ipHeaderLen + 3].toInt() and 0xFF)
        if (dstPort != 53) return

        val dnsOffset = ipHeaderLen + 8
        val dnsPayloadLen = length - dnsOffset
        if (dnsPayloadLen <= 12) return

        val dnsPayload = packet.copyOfRange(dnsOffset, length)
        val queriedDomain = extractDnsQueryDomain(dnsPayload)

        val dnsResponse: ByteArray = if (queriedDomain != null && BlocklistManager.isBlocked(queriedDomain)) {
            BlocklistManager.recordBlocked(this)
            buildBlockedDnsResponse(dnsPayload)
        } else {
            forwardDnsToUpstream(dnsPayload) ?: return
        }

        val responseIpPacket = buildUdpIpResponse(packet, ipHeaderLen, dnsResponse)
        synchronized(output) {
            output.write(responseIpPacket)
        }
    }

    private fun handleIpv6DnsPacket(packet: ByteArray, length: Int, output: FileOutputStream) {
        // IPv6 base header + UDP header. Extension headers and fragments are deliberately
        // rejected rather than misinterpreted; ordinary UDP/53 DNS is supported.
        if (length < 48 || (packet[6].toInt() and 0xFF) != 17) return
        val destination = packet.copyOfRange(24, 40)
        val expected = InetAddress.getByName("fd00:1111:2222::2").address
        if (!destination.contentEquals(expected)) return
        val udpOffset = 40
        val dstPort = ((packet[udpOffset + 2].toInt() and 0xFF) shl 8) or
                (packet[udpOffset + 3].toInt() and 0xFF)
        if (dstPort != 53) return
        val udpLength = ((packet[udpOffset + 4].toInt() and 0xFF) shl 8) or
                (packet[udpOffset + 5].toInt() and 0xFF)
        if (udpLength < 20 || udpOffset + udpLength > length) return
        val dns = packet.copyOfRange(udpOffset + 8, udpOffset + udpLength)
        val domain = extractDnsQueryDomain(dns) ?: return
        val response = if (BlocklistManager.isBlocked(domain)) {
            BlocklistManager.recordBlocked(this)
            buildBlockedDnsResponse(dns)
        } else {
            forwardDnsToUpstream(dns) ?: return
        }
        val responsePacket = buildIpv6UdpResponse(packet, response)
        synchronized(output) { output.write(responsePacket) }
    }

    private fun buildIpv6UdpResponse(original: ByteArray, dns: ByteArray): ByteArray {
        val udpLength = 8 + dns.size
        val out = ByteArray(40 + udpLength)
        out[0] = 0x60
        out[1] = 0
        out[2] = 0
        out[3] = 0
        out[4] = ((udpLength shr 8) and 0xFF).toByte()
        out[5] = (udpLength and 0xFF).toByte()
        out[6] = 17
        out[7] = 64
        // Swap IPv6 source and destination addresses.
        System.arraycopy(original, 24, out, 8, 16)
        System.arraycopy(original, 8, out, 24, 16)
        val oldUdp = 40
        out[40] = original[oldUdp + 2]
        out[41] = original[oldUdp + 3]
        out[42] = original[oldUdp]
        out[43] = original[oldUdp + 1]
        out[44] = ((udpLength shr 8) and 0xFF).toByte()
        out[45] = (udpLength and 0xFF).toByte()
        out[46] = 0
        out[47] = 0
        System.arraycopy(dns, 0, out, 48, dns.size)

        // IPv6 requires a non-zero UDP checksum.
        var sum = 0L
        fun addWords(bytes: ByteArray, start: Int, size: Int) {
            var i = start
            val end = start + size
            while (i + 1 < end) {
                sum += (((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)).toLong()
                i += 2
            }
            if (i < end) sum += ((bytes[i].toInt() and 0xFF) shl 8).toLong()
        }
        addWords(out, 8, 32)
        sum += udpLength.toLong()
        sum += 17L
        addWords(out, 40, udpLength)
        while ((sum shr 16) != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        var checksum = sum.inv().toInt() and 0xFFFF
        if (checksum == 0) checksum = 0xFFFF
        out[46] = (checksum shr 8).toByte()
        out[47] = checksum.toByte()
        return out
    }

    private fun extractDnsQueryDomain(dns: ByteArray): String? {
        return try {
            if (dns.size < 17) return null
            // QR must be zero (query); at least one question must be present.
            if ((dns[2].toInt() and 0x80) != 0) return null
            val questionCount = ((dns[4].toInt() and 0xFF) shl 8) or
                    (dns[5].toInt() and 0xFF)
            if (questionCount < 1) return null

            var pos = 12
            val labels = mutableListOf<String>()
            var totalLength = 0
            while (pos < dns.size) {
                val labelLength = dns[pos].toInt() and 0xFF
                pos++
                if (labelLength == 0) break
                // Compression pointers are not valid in a standard question QNAME.
                if (labelLength > 63 || (labelLength and 0xC0) != 0 ||
                    pos + labelLength > dns.size) return null
                totalLength += labelLength + 1
                if (totalLength > 254) return null
                val label = String(dns, pos, labelLength, Charsets.US_ASCII)
                if (label.any { it.code !in 33..126 }) return null
                labels.add(label)
                pos += labelLength
            }
            if (labels.isEmpty() || pos + 4 > dns.size) return null
            labels.joinToString(".").lowercase()
        } catch (_: Exception) {
            null
        }
    }

    private fun buildBlockedDnsResponse(query: ByteArray): ByteArray {
        val resp = query.copyOf()
        resp[2] = (0x81).toByte()
        resp[3] = (0x83).toByte()
        return resp
    }

    private fun forwardDnsToUpstream(query: ByteArray): ByteArray? {
        return try {
            DatagramSocket().use { socket ->
                if (!protect(socket)) return null
                socket.soTimeout = 4000
                val upstream = InetAddress.getByName("1.1.1.1")
                socket.send(DatagramPacket(query, query.size, upstream, 53))
                val buf = ByteArray(4096)
                val reply = DatagramPacket(buf, buf.size)
                socket.receive(reply)
                buf.copyOf(reply.length)
            }
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
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
