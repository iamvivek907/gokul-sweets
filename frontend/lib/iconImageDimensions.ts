type Dimensions = {width: number; height: number};

/** Read dimensions from supported raster headers without decoding pixels. */
export function iconImageDimensions(buffer: ArrayBuffer, mime: string): Dimensions | null {
    const bytes = new DataView(buffer);
    const length = bytes.byteLength;
    if (mime === "image/png") {
        if (length < 33 || bytes.getUint32(0) !== 0x89504e47 || bytes.getUint32(4) !== 0x0d0a1a0a ||
            bytes.getUint32(8) !== 13 || bytes.getUint32(12) !== 0x49484452) return null;
        return {width: bytes.getUint32(16), height: bytes.getUint32(20)};
    }
    if (mime === "image/jpeg") {
        if (length < 4 || bytes.getUint16(0) !== 0xffd8) return null;
        let offset = 2;
        while (offset < length && bytes.getUint8(offset++) === 0xff) {
            while (offset < length && bytes.getUint8(offset) === 0xff) offset++;
            if (offset + 3 > length) return null;
            const marker = bytes.getUint8(offset++);
            if (marker === 0xda || marker === 0xd9) return null;
            const segment = bytes.getUint16(offset);
            if (segment < 2 || offset + segment > length) return null;
            if (marker >= 0xc0 && marker <= 0xcf && ![0xc4, 0xc8, 0xcc].includes(marker)) {
                return segment >= 8 ? {width: bytes.getUint16(offset + 5), height: bytes.getUint16(offset + 3)} : null;
            }
            offset += segment;
        }
        return null;
    }
    if (mime !== "image/webp" || length < 25 || bytes.getUint32(0) !== 0x52494646 ||
        bytes.getUint32(8) !== 0x57454250) return null;
    const kind = bytes.getUint32(12);
    const chunkSize = bytes.getUint32(16, true);
    if (20 + chunkSize > length) return null;
    if (kind === 0x56503858 && chunkSize >= 10) {
        // VP8X stores dimensions as 24-bit little-endian values minus one.
        const read24 = (offset: number) => bytes.getUint16(offset, true) + bytes.getUint8(offset + 2) * 65536;
        return {width: read24(24) + 1, height: read24(27) + 1};
    }
    if (kind === 0x5650384c && chunkSize >= 5 && bytes.getUint8(20) === 0x2f) {
        const bits = bytes.getUint32(21, true);
        return {width: (bits & 0x3fff) + 1, height: ((bits >>> 14) & 0x3fff) + 1};
    }
    if (kind === 0x56503820 && chunkSize >= 10 && bytes.getUint16(23) === 0x9d01 && bytes.getUint8(25) === 0x2a) {
        return {width: bytes.getUint16(26, true) & 0x3fff, height: bytes.getUint16(28, true) & 0x3fff};
    }
    return null;
}
