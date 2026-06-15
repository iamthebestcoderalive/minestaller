const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

/**
 * NbtReader parses raw binary data in Named Binary Tag (NBT) format.
 */
class NbtReader {
    constructor(buffer) {
        this.buffer = buffer;
        this.offset = 0;
    }

    readByte() {
        const val = this.buffer.readInt8(this.offset);
        this.offset += 1;
        return val;
    }

    readShort() {
        const val = this.buffer.readInt16BE(this.offset);
        this.offset += 2;
        return val;
    }

    readInt() {
        const val = this.buffer.readInt32BE(this.offset);
        this.offset += 4;
        return val;
    }

    readLong() {
        const val = this.buffer.readBigInt64BE(this.offset);
        this.offset += 8;
        return Number(val);
    }

    readFloat() {
        const val = this.buffer.readFloatBE(this.offset);
        this.offset += 4;
        return val;
    }

    readDouble() {
        const val = this.buffer.readDoubleBE(this.offset);
        this.offset += 8;
        return val;
    }

    readString() {
        const len = this.buffer.readUInt16BE(this.offset);
        this.offset += 2;
        const str = this.buffer.toString('utf8', this.offset, this.offset + len);
        this.offset += len;
        return str;
    }

    readTagPayload(type) {
        switch (type) {
            case 0: return null;
            case 1: return this.readByte();
            case 2: return this.readShort();
            case 3: return this.readInt();
            case 4: return this.readLong();
            case 5: return this.readFloat();
            case 6: return this.readDouble();
            case 7: {
                const len = this.readInt();
                const arr = this.buffer.subarray(this.offset, this.offset + len);
                this.offset += len;
                return arr;
            }
            case 8: return this.readString();
            case 9: {
                const subType = this.readByte();
                const len = this.readInt();
                const list = [];
                for (let i = 0; i < len; i++) {
                    list.push(this.readTagPayload(subType));
                }
                return { type: subType, value: list };
            }
            case 10: {
                const compound = {};
                while (true) {
                    const subType = this.readByte();
                    if (subType === 0) break;
                    const name = this.readString();
                    const val = this.readTagPayload(subType);
                    compound[name] = { type: subType, value: val };
                }
                return compound;
            }
            case 11: {
                const len = this.readInt();
                const arr = [];
                for (let i = 0; i < len; i++) {
                    arr.push(this.readInt());
                }
                return arr;
            }
            case 12: {
                const len = this.readInt();
                const arr = [];
                for (let i = 0; i < len; i++) {
                    arr.push(this.readLong());
                }
                return arr;
            }
            default:
                throw new Error("Unknown NBT tag type: " + type);
        }
    }

    readRoot() {
        const type = this.readByte();
        if (type !== 10) {
            throw new Error("Expected root Compound tag, got " + type);
        }
        const name = this.readString();
        const value = this.readTagPayload(10);
        return { name, type, value };
    }
}

/**
 * NbtWriter serializes Javascript objects back into binary NBT format.
 */
class NbtWriter {
    constructor() {
        this.buffers = [];
    }

    writeByte(val) {
        const buf = Buffer.alloc(1);
        buf.writeInt8(val, 0);
        this.buffers.push(buf);
    }

    writeShort(val) {
        const buf = Buffer.alloc(2);
        buf.writeInt16BE(val, 0);
        this.buffers.push(buf);
    }

    writeInt(val) {
        const buf = Buffer.alloc(4);
        buf.writeInt32BE(val, 0);
        this.buffers.push(buf);
    }

    writeLong(val) {
        const buf = Buffer.alloc(8);
        buf.writeBigInt64BE(BigInt(val), 0);
        this.buffers.push(buf);
    }

    writeFloat(val) {
        const buf = Buffer.alloc(4);
        buf.writeFloatBE(val, 0);
        this.buffers.push(buf);
    }

    writeDouble(val) {
        const buf = Buffer.alloc(8);
        buf.writeDoubleBE(val, 0);
        this.buffers.push(buf);
    }

    writeString(str) {
        const strBuf = Buffer.from(str, 'utf8');
        this.writeShort(strBuf.length);
        this.buffers.push(strBuf);
    }

    writeTagPayload(type, val) {
        switch (type) {
            case 0:
                break;
            case 1:
                this.writeByte(val);
                break;
            case 2:
                this.writeShort(val);
                break;
            case 3:
                this.writeInt(val);
                break;
            case 4:
                this.writeLong(val);
                break;
            case 5:
                this.writeFloat(val);
                break;
            case 6:
                this.writeDouble(val);
                break;
            case 7:
                this.writeInt(val.length);
                this.buffers.push(Buffer.from(val));
                break;
            case 8:
                this.writeString(val);
                break;
            case 9:
                this.writeByte(val.type);
                this.writeInt(val.value.length);
                for (const item of val.value) {
                    this.writeTagPayload(val.type, item);
                }
                break;
            case 10:
                for (const [name, tag] of Object.entries(val)) {
                    this.writeByte(tag.type);
                    this.writeString(name);
                    this.writeTagPayload(tag.type, tag.value);
                }
                this.writeByte(0);
                break;
            case 11:
                this.writeInt(val.length);
                for (const item of val) {
                    this.writeInt(item);
                }
                break;
            case 12:
                this.writeInt(val.length);
                for (const item of val) {
                    this.writeLong(item);
                }
                break;
        }
    }

    writeRoot(root) {
        this.writeByte(10);
        this.writeString(root.name);
        this.writeTagPayload(10, root.value);
        return Buffer.concat(this.buffers);
    }
}

/**
 * Reads and parses level.dat from a world directory.
 * @param {string} worldDir
 * @returns {object} Uncompressed NBT root object
 */
function readLevelDat(worldDir) {
    const levelDatPath = path.join(worldDir, 'level.dat');
    if (!fs.existsSync(levelDatPath)) {
        throw new Error("level.dat not found");
    }
    const compressed = fs.readFileSync(levelDatPath);
    const uncompressed = zlib.gunzipSync(compressed);
    const reader = new NbtReader(uncompressed);
    return reader.readRoot();
}

/**
 * Serializes and writes root NBT back to level.dat.
 * @param {string} worldDir
 * @param {object} rootNbt
 */
function writeLevelDat(worldDir, rootNbt) {
    const levelDatPath = path.join(worldDir, 'level.dat');
    const writer = new NbtWriter();
    const uncompressed = writer.writeRoot(rootNbt);
    const compressed = zlib.gzipSync(uncompressed);
    fs.writeFileSync(levelDatPath, compressed);
}

module.exports = {
    NbtReader,
    NbtWriter,
    readLevelDat,
    writeLevelDat
};
