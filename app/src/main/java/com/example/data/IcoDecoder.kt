package com.example.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.decode.ImageSource
import coil.fetch.SourceResult
import coil.request.Options
import coil.size.pxOrElse
import java.io.InputStream
import kotlin.math.abs

/**
 * Pure-Kotlin Windows .ico / .cur decoder for Coil.
 * Handles both PNG-compressed ICO entries and uncompressed DIB (1/4/8/24/32 bpp) entries with AND masks.
 */
class IcoDecoder(
    private val source: ImageSource,
    private val options: Options
) : Decoder {

    override suspend fun decode(): DecodeResult? {
        val bytes = source.source().readByteArray()
        val targetW = options.size.width.pxOrElse { 256 }
        val targetH = options.size.height.pxOrElse { 256 }
        val targetDim = maxOf(targetW, targetH).coerceIn(16, 512)
        val bitmap = decodeBytesToBitmap(bytes, targetDim) ?: return null
        return DecodeResult(
            drawable = BitmapDrawable(options.context.resources, bitmap),
            isSampled = false
        )
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            val mime = result.mimeType?.lowercase()
            if (mime == "image/x-icon" || mime == "image/vnd.microsoft.icon") {
                return IcoDecoder(result.source, options)
            }
            val buffered = result.source.source()
            if (!buffered.request(6)) return null
            val peek = buffered.peek()
            val b0 = peek.readByte().toInt() and 0xFF
            val b1 = peek.readByte().toInt() and 0xFF
            val b2 = peek.readByte().toInt() and 0xFF
            val b3 = peek.readByte().toInt() and 0xFF
            val countLo = peek.readByte().toInt() and 0xFF
            val countHi = peek.readByte().toInt() and 0xFF
            val count = countLo or (countHi shl 8)
            val isIcoHeader = b0 == 0 && b1 == 0 && (b2 == 1 || b2 == 2) && b3 == 0 && count in 1..256
            return if (isIcoHeader) IcoDecoder(result.source, options) else null
        }
    }

    companion object {
        private data class IconDirEntry(
            val width: Int,
            val height: Int,
            val colorCount: Int,
            val bitCount: Int,
            val bytesInRes: Int,
            val imageOffset: Int
        )

        fun inspectDimensions(input: InputStream): Pair<Int, Int>? {
            val header = ByteArray(6 + 16 * 32)
            val read = readUpTo(input, header)
            if (read < 22) return null
            val entries = parseEntries(header, read) ?: return null
            val best = entries.maxByOrNull { it.width.toLong() * it.height.toLong() * 100L + it.bitCount }
                ?: return null
            return best.width to best.height
        }

        fun decodeBytesToBitmap(bytes: ByteArray, preferredSize: Int = 256): Bitmap? {
            if (bytes.size < 22) return null
            val entries = parseEntries(bytes, bytes.size) ?: return null
            val chosen = selectBestEntry(entries, preferredSize) ?: return null
            val start = chosen.imageOffset
            val end = (start.toLong() + chosen.bytesInRes.toLong()).coerceAtMost(bytes.size.toLong()).toInt()
            if (start < 0 || start >= bytes.size || end - start < 16) return null

            // 1. Check PNG signature inside entry: 89 50 4E 47 0D 0A 1A 0A
            if (end - start >= 8 &&
                (bytes[start].toInt() and 0xFF) == 0x89 &&
                (bytes[start + 1].toInt() and 0xFF) == 0x50 &&
                (bytes[start + 2].toInt() and 0xFF) == 0x4E &&
                (bytes[start + 3].toInt() and 0xFF) == 0x47
            ) {
                return BitmapFactory.decodeByteArray(bytes, start, end - start)
            }

            // 2. Parse BITMAPINFOHEADER DIB payload
            return decodeDibEntry(bytes, start, end, chosen)
        }

        private fun readUpTo(input: InputStream, dest: ByteArray): Int {
            var total = 0
            while (total < dest.size) {
                val n = input.read(dest, total, dest.size - total)
                if (n <= 0) break
                total += n
            }
            return total
        }

        private fun parseEntries(bytes: ByteArray, limit: Int): List<IconDirEntry>? {
            if (limit < 6) return null
            val reserved = u16(bytes, 0)
            val type = u16(bytes, 2)
            val count = u16(bytes, 4)
            if (reserved != 0 || (type != 1 && type != 2) || count !in 1..256) return null
            val list = ArrayList<IconDirEntry>(count)
            for (i in 0 until count) {
                val off = 6 + i * 16
                if (off + 16 > limit) break
                val rawW = bytes[off].toInt() and 0xFF
                val rawH = bytes[off + 1].toInt() and 0xFF
                val colorCount = bytes[off + 2].toInt() and 0xFF
                val bitCount = u16(bytes, off + 6)
                val bytesInRes = u32(bytes, off + 8)
                val imageOffset = u32(bytes, off + 12)
                if (bytesInRes <= 0 || imageOffset <= 0) continue
                list.add(
                    IconDirEntry(
                        width = if (rawW == 0) 256 else rawW,
                        height = if (rawH == 0) 256 else rawH,
                        colorCount = colorCount,
                        bitCount = bitCount,
                        bytesInRes = bytesInRes,
                        imageOffset = imageOffset
                    )
                )
            }
            return list.takeIf { it.isNotEmpty() }
        }

        private fun selectBestEntry(entries: List<IconDirEntry>, preferredSize: Int): IconDirEntry? {
            return entries.minWithOrNull(
                compareBy<IconDirEntry> { entry ->
                    val maxDim = maxOf(entry.width, entry.height)
                    // Prefer entries >= preferredSize, smallest delta above target first; else largest available
                    if (maxDim >= preferredSize) maxDim - preferredSize else (preferredSize - maxDim) + 10_000
                }.thenByDescending { it.bitCount }.thenByDescending { it.width * it.height }
            )
        }

        private fun decodeDibEntry(
            bytes: ByteArray,
            start: Int,
            end: Int,
            entry: IconDirEntry
        ): Bitmap? {
            if (end - start < 40) return null
            val headerSize = u32(bytes, start)
            if (headerSize < 40 || start + headerSize > end) return null

            val dibW = s32(bytes, start + 4).let { if (it > 0) it else entry.width }
            val rawDibH = s32(bytes, start + 8)
            val topDown = rawDibH < 0
            // ICO DIB stores double height (XOR + AND mask)
            val dibH = (abs(rawDibH) / 2).let { if (it > 0) it else entry.height }
            if (dibW !in 1..512 || dibH !in 1..512) return null

            val bpp = u16(bytes, start + 14).let { if (it > 0) it else entry.bitCount }
            val compression = u32(bytes, start + 16)
            if (compression != 0 && compression != 3) return null

            val colorsUsed = u32(bytes, start + 32)
            val paletteEntries = when (bpp) {
                1 -> if (colorsUsed > 0) colorsUsed else 2
                4 -> if (colorsUsed > 0) colorsUsed else 16
                8 -> if (colorsUsed > 0) colorsUsed else 256
                else -> 0
            }.coerceIn(0, 256)

            val paletteOffset = start + headerSize
            val pixelDataOffset = paletteOffset + paletteEntries * 4
            if (pixelDataOffset >= end) return null

            val palette = IntArray(paletteEntries)
            for (i in 0 until paletteEntries) {
                val pOff = paletteOffset + i * 4
                if (pOff + 4 > end) break
                val b = bytes[pOff].toInt() and 0xFF
                val g = bytes[pOff + 1].toInt() and 0xFF
                val r = bytes[pOff + 2].toInt() and 0xFF
                palette[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }

            val xorRowStride = ((dibW * bpp + 31) / 32) * 4
            val andRowStride = ((dibW + 31) / 32) * 4
            val andMaskOffset = pixelDataOffset + xorRowStride * dibH
            val hasAndMask = andMaskOffset + andRowStride * dibH <= end

            val pixels = IntArray(dibW * dibH)
            var hasNonZeroAlpha = false

            for (row in 0 until dibH) {
                val y = if (topDown) row else (dibH - 1 - row)
                val rowOff = pixelDataOffset + row * xorRowStride
                val andOff = andMaskOffset + row * andRowStride
                if (rowOff + xorRowStride > end) break

                for (x in 0 until dibW) {
                    var argb = 0
                    when (bpp) {
                        32 -> {
                            val p = rowOff + x * 4
                            val b = bytes[p].toInt() and 0xFF
                            val g = bytes[p + 1].toInt() and 0xFF
                            val r = bytes[p + 2].toInt() and 0xFF
                            val a = bytes[p + 3].toInt() and 0xFF
                            if (a != 0) hasNonZeroAlpha = true
                            argb = (a shl 24) or (r shl 16) or (g shl 8) or b
                        }
                        24 -> {
                            val p = rowOff + x * 3
                            val b = bytes[p].toInt() and 0xFF
                            val g = bytes[p + 1].toInt() and 0xFF
                            val r = bytes[p + 2].toInt() and 0xFF
                            argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        }
                        8 -> {
                            val idx = (bytes[rowOff + x].toInt() and 0xFF)
                            argb = palette.getOrElse(idx) { 0 }
                        }
                        4 -> {
                            val byteVal = bytes[rowOff + (x ushr 1)].toInt() and 0xFF
                            val idx = if ((x and 1) == 0) (byteVal ushr 4) and 0xF else byteVal and 0xF
                            argb = palette.getOrElse(idx) { 0 }
                        }
                        1 -> {
                            val byteVal = bytes[rowOff + (x ushr 3)].toInt() and 0xFF
                            val idx = (byteVal ushr (7 - (x and 7))) and 1
                            argb = palette.getOrElse(idx) { 0 }
                        }
                    }

                    if (bpp != 32 && hasAndMask) {
                        val maskByte = bytes[andOff + (x ushr 3)].toInt() and 0xFF
                        val masked = ((maskByte ushr (7 - (x and 7))) and 1) == 1
                        if (masked) argb = argb and 0x00FFFFFF
                    }
                    pixels[y * dibW + x] = argb
                }
            }

            // Legacy 32bpp BMP-in-ICO sometimes writes 0x00 in all alpha bytes and relies on the AND mask
            if (bpp == 32 && !hasNonZeroAlpha && hasAndMask) {
                for (row in 0 until dibH) {
                    val y = if (topDown) row else (dibH - 1 - row)
                    val andOff = andMaskOffset + row * andRowStride
                    for (x in 0 until dibW) {
                        val maskByte = bytes[andOff + (x ushr 3)].toInt() and 0xFF
                        val masked = ((maskByte ushr (7 - (x and 7))) and 1) == 1
                        val idx = y * dibW + x
                        pixels[idx] = if (masked) (pixels[idx] and 0x00FFFFFF) else (pixels[idx] or (0xFF shl 24))
                    }
                }
            }

            return Bitmap.createBitmap(pixels, dibW, dibH, Bitmap.Config.ARGB_8888)
        }

        private fun u16(b: ByteArray, o: Int): Int =
            (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

        private fun u32(b: ByteArray, o: Int): Int =
            (b[o].toInt() and 0xFF) or
                ((b[o + 1].toInt() and 0xFF) shl 8) or
                ((b[o + 2].toInt() and 0xFF) shl 16) or
                ((b[o + 3].toInt() and 0xFF) shl 24)

        private fun s32(b: ByteArray, o: Int): Int = u32(b, o)
    }
}
