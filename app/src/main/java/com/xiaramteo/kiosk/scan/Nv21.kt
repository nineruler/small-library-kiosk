package com.xiaramteo.kiosk.scan

/**
 * NV21 프레임에서 가운데 영역만 잘라낸다.
 *
 * ML Kit 은 입력 이미지를 내부에서 축소해 바코드를 찾는다. 1920x1080 프레임에서 바코드가
 * 200px 남짓밖에 안 되면 축소 과정에서 막대 간격이 뭉개져 인식에 실패한다.
 * 화면의 안내 네모에 해당하는 가운데만 원본 해상도로 잘라 넘기면 바코드가 차지하는
 * 비율이 올라가 인식률이 크게 좋아진다.
 *
 * NV21 은 Y 평면(w*h) 뒤에 VU 가 2x2 서브샘플링으로 붙으므로, 좌표와 크기를 모두 짝수로 맞춘다.
 */
fun cropNv21(
    source: ByteArray,
    width: Int,
    height: Int,
    widthRatio: Float,
    heightRatio: Float,
): Nv21Frame {
    val cropW = ((width * widthRatio).toInt() / 2) * 2
    val cropH = ((height * heightRatio).toInt() / 2) * 2
    if (cropW <= 0 || cropH <= 0 || cropW > width || cropH > height) {
        return Nv21Frame(source, width, height)
    }
    val left = ((width - cropW) / 2 / 2) * 2
    val top = ((height - cropH) / 2 / 2) * 2

    val out = ByteArray(cropW * cropH * 3 / 2)
    // Y 평면
    for (row in 0 until cropH) {
        System.arraycopy(source, (top + row) * width + left, out, row * cropW, cropW)
    }
    // VU 평면 (세로 절반)
    val srcUvOffset = width * height
    val dstUvOffset = cropW * cropH
    for (row in 0 until cropH / 2) {
        System.arraycopy(
            source,
            srcUvOffset + (top / 2 + row) * width + left,
            out,
            dstUvOffset + row * cropW,
            cropW,
        )
    }
    return Nv21Frame(out, cropW, cropH)
}

data class Nv21Frame(val bytes: ByteArray, val width: Int, val height: Int) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is Nv21Frame && width == other.width && height == other.height &&
            bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = (width * 31 + height) * 31 + bytes.contentHashCode()
}
