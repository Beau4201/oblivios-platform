package org.sovereignos.airpods.app

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.lang.reflect.InvocationTargetException

/** Opens the L2CAP channel of the accessory protocol. */
internal fun interface AirPodsSocketOpener {
    @Throws(ReflectiveOperationException::class)
    fun open(device: BluetoothDevice, psm: Int): BluetoothSocket
}

/**
 * BluetoothDevice#createL2capSocket(int) is @Hide (BluetoothDevice.java:2775) and is the only way to
 * a BR/EDR L2CAP channel with a fixed PSM: the public createL2capChannel and
 * createUsingSocketSettings take LE PSMs 128 to 255 only. The call is possible only because this
 * package is on the hidden-API allow-list (sysconfig/hidden-api-whitelisted-app-*.xml); in any
 * other package getMethod throws NoSuchMethodException.
 */
internal object HiddenL2capOpener : AirPodsSocketOpener {
    override fun open(device: BluetoothDevice, psm: Int): BluetoothSocket {
        val method = BluetoothDevice::class.java.getMethod("createL2capSocket", Int::class.javaPrimitiveType)
        try {
            return method.invoke(device, psm) as BluetoothSocket
        } catch (e: InvocationTargetException) {
            // The method exists and ran: its own failure (an IOException, a SecurityException) is a failed connect that
            // is retried, not proof that the image lacks the method.
            throw e.targetException ?: e
        }
    }
}
