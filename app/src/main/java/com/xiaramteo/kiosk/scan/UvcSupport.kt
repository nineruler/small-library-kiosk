package com.xiaramteo.kiosk.scan

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

/**
 * 꽂혀 있는 USB 웹캠(UVC 기기)을 찾는다.
 *
 * 이 태블릿의 커널에는 uvcvideo 드라이버가 없어 camera2가 외장 카메라를 노출하지 않는다.
 * 대신 usbfs는 열려 있으므로, libuvc가 유저스페이스에서 직접 기기를 다룰 수 있다.
 * 판별은 USB 인터페이스 클래스 14(Video)로 한다.
 */
fun findUvcDevice(context: Context): UsbDevice? {
    val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
    return manager.deviceList.values.firstOrNull { device ->
        (0 until device.interfaceCount).any { i ->
            device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_VIDEO
        }
    }
}
