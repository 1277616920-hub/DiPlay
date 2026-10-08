package com.shihabal3amri.diplay

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

object ShilapiUsb {
    private const val TAG = "ShilapiDiPlay"
    private const val SHILAPI_VID = 0x1A86
    private const val SHILAPI_PID = 0x7523

    private var usbManager: UsbManager? = null
    private var conn: UsbDeviceConnection? = null
    private var epIn: UsbEndpoint? = null
    private var epOut: UsbEndpoint? = null
    private var running = AtomicBoolean(false)

    fun init(ctx: Context) {
        usbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    fun scanDevice(): UsbDevice? {
        val deviceList = usbManager?.deviceList ?: return null
        for ((_, dev) in deviceList) {
            if (dev.vendorId == SHILAPI_VID && dev.productId == SHILAPI_PID) {
                Log.i(TAG, "✅ 找到Shilapi硬件MFi模块")
                return dev
            }
        }
        return null
    }

    fun open(dev: UsbDevice): Boolean {
        if (!usbManager!!.hasPermission(dev)) return false
        conn = usbManager!!.openDevice(dev)
        val iface: UsbInterface = dev.getInterface(0)
        conn?.claimInterface(iface, true)
        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            if (ep.type == UsbEndpoint.TYPE_BULK) {
                if (ep.direction == UsbEndpoint.IN) epIn = ep
                else epOut = ep
            }
        }
        return epIn != null && epOut != null
    }

    fun startReceive(onVideo: (ByteArray) -> Unit, onAudio: (ByteArray) -> Unit, onControl: (ByteArray) -> Unit) {
        if (running.get()) return
        running.set(true)
        Thread {
            val buf = ByteArray(1024)
            while (running.get()) {
                val len = conn?.bulkTransfer(epIn, buf, buf.size, 1000) ?: -1
                if (len > 0) {
                    val pkt = buf.copyOfRange(0, len)
                    when (pkt[0]) {
                        0x01.toByte() -> onVideo(pkt.copyOfRange(1, pkt.size))
                        0x02.toByte() -> onAudio(pkt.copyOfRange(1, pkt.size))
                        0x03.toByte() -> onControl(pkt.copyOfRange(1, pkt.size))
                    }
                }
            }
        }.start()
    }

    fun sendCtrl(data: ByteArray) {
        conn?.bulkTransfer(epOut, data, data.size, 1000)
    }

    fun stop() {
        running.set(false)
        conn?.close()
        conn = null
    }
}