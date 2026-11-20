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
        var active = false
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
            .setContentText("Filtering ads, trackers & popups system-wide")
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
            .setBlocking(true)

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
    }

    private fun forwardDnsToUpstream(query: ByteArray): ByteArray? {
        return try {
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
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
