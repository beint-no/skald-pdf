package org.skaldpdf.barcode;

import org.skaldpdf.image.ImageSource;
import org.skaldpdf.pdf.PdfDocument;
import org.skaldpdf.pdf.PdfNumbers;
import org.skaldpdf.pdf.PdfPage;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * An immutable QR Code (ISO/IEC 18004 Model 2) drawn as vector modules.
 * Versions 1–16, byte mode, with automatic version selection.
 */
public final class QrCode implements ImageSource {
    public enum Ecc {
        L(1), M(0), Q(3), H(2);

        private final int formatBits;

        Ecc(int formatBits) {
            this.formatBits = formatBits;
        }
    }

    private static final int QUIET_ZONE = 4;
    private static final int MAXIMUM_VERSION = 16;
    private static final int[][] ALIGNMENT = {
        {}, {},
        {6, 18}, {6, 22}, {6, 26}, {6, 30}, {6, 34},
        {6, 22, 38}, {6, 24, 42}, {6, 26, 46}, {6, 28, 50},
        {6, 30, 54}, {6, 32, 58}, {6, 34, 62},
        {6, 26, 46, 66}, {6, 26, 48, 70}, {6, 26, 50, 74}
    };
    /**
     * Per version, per ECC (L,M,Q,H):
     * {ecPerBlock, group1Blocks, group1Data, group2Blocks, group2Data}.
     */
    private static final int[][][] BLOCKS = {
        {},
        {{7, 1, 19, 0, 0}, {10, 1, 16, 0, 0}, {13, 1, 13, 0, 0}, {17, 1, 9, 0, 0}},
        {{10, 1, 34, 0, 0}, {16, 1, 28, 0, 0}, {22, 1, 22, 0, 0}, {28, 1, 16, 0, 0}},
        {{15, 1, 55, 0, 0}, {26, 1, 44, 0, 0}, {18, 2, 17, 0, 0}, {22, 2, 13, 0, 0}},
        {{20, 1, 80, 0, 0}, {18, 2, 32, 0, 0}, {26, 2, 24, 0, 0}, {16, 4, 9, 0, 0}},
        {{26, 1, 108, 0, 0}, {24, 2, 43, 0, 0}, {18, 2, 15, 2, 16}, {22, 2, 11, 2, 12}},
        {{18, 2, 68, 0, 0}, {16, 4, 27, 0, 0}, {24, 4, 19, 0, 0}, {28, 4, 15, 0, 0}},
        {{20, 2, 78, 0, 0}, {18, 4, 31, 0, 0}, {18, 2, 14, 4, 15}, {26, 4, 13, 1, 14}},
        {{24, 2, 97, 0, 0}, {22, 2, 38, 2, 39}, {22, 4, 18, 2, 19}, {26, 4, 14, 2, 15}},
        {{30, 2, 116, 0, 0}, {22, 3, 36, 2, 37}, {20, 4, 16, 4, 17}, {24, 4, 12, 4, 13}},
        {{18, 2, 68, 2, 69}, {26, 4, 43, 1, 44}, {24, 6, 19, 2, 20}, {28, 6, 15, 2, 16}},
        {{20, 4, 81, 0, 0}, {30, 1, 50, 4, 51}, {28, 4, 22, 4, 23}, {24, 3, 12, 8, 13}},
        {{24, 2, 92, 2, 93}, {22, 6, 36, 2, 37}, {26, 4, 20, 6, 21}, {28, 7, 14, 4, 15}},
        {{26, 4, 107, 0, 0}, {22, 8, 37, 1, 38}, {24, 8, 20, 4, 21}, {22, 12, 11, 4, 12}},
        {{30, 3, 115, 1, 116}, {24, 4, 40, 5, 41}, {20, 11, 16, 5, 17}, {24, 11, 12, 5, 13}},
        {{22, 5, 87, 1, 88}, {24, 5, 41, 5, 42}, {30, 5, 24, 7, 25}, {24, 11, 12, 7, 13}},
        {{24, 5, 98, 1, 99}, {28, 7, 45, 3, 46}, {24, 15, 19, 2, 20}, {30, 3, 15, 13, 16}}
    };
    private static final int[] GF_EXP = new int[512];
    private static final int[] GF_LOG = new int[256];

    static {
        var value = 1;
        for (int index = 0; index < 255; index++) {
            GF_EXP[index] = value;
            GF_LOG[value] = index;
            value <<= 1;
            if (value >= 256) {
                value ^= 0x11d;
            }
        }
        System.arraycopy(GF_EXP, 0, GF_EXP, 255, 255);
    }

    private final String value;
    private final Ecc ecc;
    private final int version;
    private final int moduleCount;
    private final boolean[][] modules;
    private final float moduleSize;

    public QrCode(String value) {
        this(value, Ecc.M, 3f);
    }

    public QrCode(String value, Ecc ecc) {
        this(value, ecc, 3f);
    }

    private QrCode(String value, Ecc ecc, float moduleSize) {
        this.value = Objects.requireNonNull(value, "value");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("QR payload must not be empty");
        }
        this.ecc = Objects.requireNonNull(ecc, "ecc");
        this.moduleSize = positive(moduleSize, "moduleSize");
        var payload = value.getBytes(StandardCharsets.UTF_8);
        this.version = chooseVersion(payload.length, ecc);
        this.moduleCount = this.version * 4 + 17;
        this.modules = encode(payload, this.version, ecc, moduleCount);
    }

    public QrCode withModuleSize(float value) {
        return new QrCode(this.value, ecc, value);
    }

    public String value() {
        return value;
    }

    public Ecc ecc() {
        return ecc;
    }

    public int version() {
        return version;
    }

    public int moduleCount() {
        return moduleCount;
    }

    public boolean module(int x, int y) {
        return modules[y][x];
    }

    @Override
    public float intrinsicWidth() {
        return (moduleCount + 2 * QUIET_ZONE) * moduleSize;
    }

    @Override
    public float intrinsicHeight() {
        return intrinsicWidth();
    }

    @Override
    public void drawOn(PdfDocument document, PdfPage page, float x, float y, float width, float height) {
        Objects.requireNonNull(document, "document").ensureOpen();
        Objects.requireNonNull(page, "page");
        positive(width, "width");
        positive(height, "height");
        var modulesAcross = moduleCount + 2 * QUIET_ZONE;
        var cellW = width / modulesAcross;
        var cellH = height / modulesAcross;
        var operators = new StringBuilder("q\n0 0 0 rg\n");
        for (int row = 0; row < moduleCount; row++) {
            var runStart = -1;
            for (int column = 0; column <= moduleCount; column++) {
                var dark = column < moduleCount && modules[row][column];
                if (dark && runStart < 0) {
                    runStart = column;
                } else if (!dark && runStart >= 0) {
                    operators.append(number(x + (runStart + QUIET_ZONE) * cellW)).append(' ')
                        .append(number(y + (modulesAcross - (row + 1 + QUIET_ZONE)) * cellH)).append(' ')
                        .append(number((column - runStart) * cellW)).append(' ')
                        .append(number(cellH)).append(" re\n");
                    runStart = -1;
                }
            }
        }
        page.append(operators.append("f\nQ\n").toString());
    }

    private static boolean[][] encode(byte[] payload, int version, Ecc ecc, int size) {
        var reserved = new boolean[size][size];
        var modules = new boolean[size][size];
        drawFunctionPatterns(modules, reserved, version);
        var codewords = interleavedCodewords(payload, version, ecc);
        placeData(modules, reserved, codewords);
        return chooseMask(modules, reserved, version, ecc);
    }

    private static int chooseVersion(int byteLength, Ecc ecc) {
        for (int version = 1; version <= MAXIMUM_VERSION; version++) {
            if (byteLength <= byteCapacity(version, ecc)) {
                return version;
            }
        }
        throw new IllegalArgumentException(
            "QR payload is too long for versions 1-" + MAXIMUM_VERSION + " at ECC " + ecc);
    }

    private static int byteCapacity(int version, Ecc ecc) {
        var dataBits = dataCodewords(version, ecc) * 8;
        var countBits = version <= 9 ? 8 : 16;
        return Math.max(0, (dataBits - 4 - 8 - 4 - countBits) / 8);
    }

    private static int dataCodewords(int version, Ecc ecc) {
        var spec = BLOCKS[version][ecc.ordinal()];
        return spec[1] * spec[2] + spec[3] * spec[4];
    }

    private static byte[] interleavedCodewords(byte[] payload, int version, Ecc ecc) {
        var spec = BLOCKS[version][ecc.ordinal()];
        var ecPerBlock = spec[0];
        var group1Blocks = spec[1];
        var group1Data = spec[2];
        var group2Blocks = spec[3];
        var group2Data = spec[4];
        var dataCapacity = group1Blocks * group1Data + group2Blocks * group2Data;
        var data = encodeData(payload, version, dataCapacity);
        var blockCount = group1Blocks + group2Blocks;
        var dataBlocks = new byte[blockCount][];
        var ecBlocks = new byte[blockCount][];
        var offset = 0;
        for (int block = 0; block < blockCount; block++) {
            var length = block < group1Blocks ? group1Data : group2Data;
            var blockData = Arrays.copyOfRange(data, offset, offset + length);
            offset += length;
            dataBlocks[block] = blockData;
            ecBlocks[block] = reedSolomon(blockData, ecPerBlock);
        }
        var total = dataCapacity + blockCount * ecPerBlock;
        var result = new byte[total];
        var index = 0;
        var maxData = Math.max(group1Data, group2Data);
        for (int column = 0; column < maxData; column++) {
            for (int block = 0; block < blockCount; block++) {
                if (column < dataBlocks[block].length) {
                    result[index++] = dataBlocks[block][column];
                }
            }
        }
        for (int column = 0; column < ecPerBlock; column++) {
            for (int block = 0; block < blockCount; block++) {
                result[index++] = ecBlocks[block][column];
            }
        }
        return result;
    }

    private static byte[] encodeData(byte[] payload, int version, int dataCapacity) {
        var bits = new BitBuffer();
        bits.append(0b0111, 4);
        bits.append(26, 8);
        bits.append(0b0100, 4);
        bits.append(payload.length, version <= 9 ? 8 : 16);
        for (var value : payload) {
            bits.append(value & 0xff, 8);
        }
        var capacityBits = dataCapacity * 8;
        bits.append(0, Math.min(4, capacityBits - bits.length()));
        while (bits.length() % 8 != 0) {
            bits.append(0, 1);
        }
        var pad = true;
        while (bits.length() < capacityBits) {
            bits.append(pad ? 0xec : 0x11, 8);
            pad = !pad;
        }
        return bits.toBytes();
    }

    private static void drawFunctionPatterns(boolean[][] modules, boolean[][] reserved, int version) {
        var size = modules.length;
        for (int index = 0; index < size; index++) {
            setFunction(modules, reserved, 6, index, index % 2 == 0);
            setFunction(modules, reserved, index, 6, index % 2 == 0);
        }
        drawFinder(modules, reserved, 0, 0);
        drawFinder(modules, reserved, size - 7, 0);
        drawFinder(modules, reserved, 0, size - 7);
        for (var row : ALIGNMENT[version]) {
            for (var column : ALIGNMENT[version]) {
                if (isFinderOverlap(row, column, size)) {
                    continue;
                }
                drawAlignment(modules, reserved, column, row);
            }
        }
        for (int index = 0; index < 6; index++) {
            reserve(reserved, 8, index);
            reserve(reserved, index, 8);
        }
        reserve(reserved, 8, 7);
        reserve(reserved, 8, 8);
        reserve(reserved, 7, 8);
        for (int index = 9; index < 15; index++) {
            reserve(reserved, 14 - index, 8);
        }
        for (int index = 0; index < 8; index++) {
            reserve(reserved, size - 1 - index, 8);
        }
        for (int index = 8; index < 15; index++) {
            reserve(reserved, 8, size - 15 + index);
        }
        setFunction(modules, reserved, 8, size - 8, true);
        if (version >= 7) {
            for (int row = 0; row < 6; row++) {
                for (int column = 0; column < 3; column++) {
                    reserve(reserved, row, size - 11 + column);
                    reserve(reserved, size - 11 + column, row);
                }
            }
        }
    }

    private static boolean isFinderOverlap(int row, int column, int size) {
        return row <= 8 && column <= 8
            || row <= 8 && column >= size - 9
            || row >= size - 9 && column <= 8;
    }

    private static void drawFinder(boolean[][] modules, boolean[][] reserved, int left, int top) {
        for (int dy = -1; dy <= 7; dy++) {
            for (int dx = -1; dx <= 7; dx++) {
                var x = left + dx;
                var y = top + dy;
                if (x < 0 || y < 0 || x >= modules.length || y >= modules.length) {
                    continue;
                }
                var dark = dx >= 0 && dx <= 6 && dy >= 0 && dy <= 6
                    && (dx == 0 || dx == 6 || dy == 0 || dy == 6 || (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4));
                setFunction(modules, reserved, x, y, dark);
            }
        }
    }

    private static void drawAlignment(boolean[][] modules, boolean[][] reserved, int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                var dark = Math.max(Math.abs(dx), Math.abs(dy)) != 1;
                setFunction(modules, reserved, cx + dx, cy + dy, dark);
            }
        }
    }

    private static void placeData(boolean[][] modules, boolean[][] reserved, byte[] codewords) {
        var size = modules.length;
        var bitIndex = 0;
        var totalBits = codewords.length * 8;
        var upward = true;
        for (int column = size - 1; column > 0; column -= 2) {
            if (column == 6) {
                column--;
            }
            // column 6 is the vertical timing pattern; skipping it keeps 2-wide strips aligned.
            for (int index = 0; index < size; index++) {
                var row = upward ? size - 1 - index : index;
                for (int offset = 0; offset < 2; offset++) {
                    var x = column - offset;
                    if (reserved[row][x] || bitIndex >= totalBits) {
                        continue;
                    }
                    var bit = ((codewords[bitIndex >>> 3] >>> (7 - (bitIndex & 7))) & 1) != 0;
                    modules[row][x] = bit;
                    bitIndex++;
                }
            }
            upward = !upward;
        }
    }

    private static boolean[][] chooseMask(boolean[][] data, boolean[][] reserved, int version, Ecc ecc) {
        var bestScore = Integer.MAX_VALUE;
        boolean[][] best = null;
        for (int mask = 0; mask < 8; mask++) {
            var candidate = applyMask(data, reserved, mask);
            drawFormat(candidate, version, ecc, mask);
            var score = penalty(candidate);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean[][] applyMask(boolean[][] data, boolean[][] reserved, int mask) {
        var size = data.length;
        var result = new boolean[size][size];
        for (int row = 0; row < size; row++) {
            System.arraycopy(data[row], 0, result[row], 0, size);
            for (int column = 0; column < size; column++) {
                if (!reserved[row][column] && masked(mask, row, column)) {
                    result[row][column] = !result[row][column];
                }
            }
        }
        return result;
    }

    private static boolean masked(int mask, int row, int column) {
        return switch (mask) {
            case 0 -> (row + column) % 2 == 0;
            case 1 -> row % 2 == 0;
            case 2 -> column % 3 == 0;
            case 3 -> (row + column) % 3 == 0;
            case 4 -> (row / 2 + column / 3) % 2 == 0;
            case 5 -> (row * column) % 2 + (row * column) % 3 == 0;
            case 6 -> ((row * column) % 2 + (row * column) % 3) % 2 == 0;
            case 7 -> ((row + column) % 2 + (row * column) % 3) % 2 == 0;
            default -> throw new AssertionError();
        };
    }

    private static void drawFormat(boolean[][] modules, int version, Ecc ecc, int mask) {
        var format = formatBits(ecc, mask);
        var size = modules.length;
        for (int index = 0; index < 15; index++) {
            var dark = ((format >>> index) & 1) != 0;
            if (index < 6) {
                modules[index][8] = dark;
            } else if (index < 8) {
                modules[index + 1][8] = dark;
            } else {
                modules[size - 15 + index][8] = dark;
            }
            if (index < 8) {
                modules[8][size - 1 - index] = dark;
            } else if (index < 9) {
                modules[8][15 - index] = dark;
            } else {
                modules[8][14 - index] = dark;
            }
        }
        if (version < 7) {
            return;
        }
        var bits = versionBits(version);
        for (int index = 0; index < 18; index++) {
            var dark = ((bits >>> index) & 1) != 0;
            var row = index / 3;
            var column = size - 11 + index % 3;
            modules[row][column] = dark;
            modules[column][row] = dark;
        }
    }

    private static int formatBits(Ecc ecc, int mask) {
        var data = (ecc.formatBits << 3) | mask;
        var rem = data;
        for (int index = 0; index < 10; index++) {
            rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        }
        return ((data << 10) | rem) ^ 0x5412;
    }

    private static int versionBits(int version) {
        var rem = version;
        for (int index = 0; index < 12; index++) {
            rem = (rem << 1) ^ ((rem >>> 11) * 0x1f25);
        }
        return (version << 12) | rem;
    }

    private static int penalty(boolean[][] modules) {
        var size = modules.length;
        var score = 0;
        for (int row = 0; row < size; row++) {
            score += runPenalty(modules, row, true);
            score += runPenalty(modules, row, false);
        }
        for (int row = 0; row < size - 1; row++) {
            for (int column = 0; column < size - 1; column++) {
                var dark = modules[row][column];
                if (dark == modules[row][column + 1]
                    && dark == modules[row + 1][column]
                    && dark == modules[row + 1][column + 1]) {
                    score += 3;
                }
            }
        }
        for (int row = 0; row < size; row++) {
            score += finderPenalty(modules, row, true);
            score += finderPenalty(modules, row, false);
        }
        var dark = 0;
        for (var row : modules) {
            for (var module : row) {
                if (module) {
                    dark++;
                }
            }
        }
        var percent = dark * 100 / (size * size);
        score += (Math.abs(percent - 50) / 5) * 10;
        return score;
    }

    private static int runPenalty(boolean[][] modules, int index, boolean horizontal) {
        var size = modules.length;
        var score = 0;
        var run = 1;
        var previous = module(modules, index, 0, horizontal);
        for (int cursor = 1; cursor < size; cursor++) {
            var current = module(modules, index, cursor, horizontal);
            if (current == previous) {
                run++;
            } else {
                if (run >= 5) {
                    score += 3 + (run - 5);
                }
                run = 1;
                previous = current;
            }
        }
        if (run >= 5) {
            score += 3 + (run - 5);
        }
        return score;
    }

    private static int finderPenalty(boolean[][] modules, int index, boolean horizontal) {
        var size = modules.length;
        var score = 0;
        for (int start = 0; start + 7 <= size; start++) {
            if (!isFinderSequence(modules, index, start, horizontal)) {
                continue;
            }
            var leftWhite = start >= 4 && allWhite(modules, index, start - 4, 4, horizontal);
            var rightWhite = start + 11 <= size && allWhite(modules, index, start + 7, 4, horizontal);
            if (leftWhite || rightWhite) {
                score += 40;
            }
        }
        return score;
    }

    private static boolean isFinderSequence(boolean[][] modules, int index, int start, boolean horizontal) {
        return module(modules, index, start, horizontal)
            && !module(modules, index, start + 1, horizontal)
            && module(modules, index, start + 2, horizontal)
            && module(modules, index, start + 3, horizontal)
            && module(modules, index, start + 4, horizontal)
            && !module(modules, index, start + 5, horizontal)
            && module(modules, index, start + 6, horizontal);
    }

    private static boolean allWhite(boolean[][] modules, int index, int start, int length, boolean horizontal) {
        for (int cursor = 0; cursor < length; cursor++) {
            if (module(modules, index, start + cursor, horizontal)) {
                return false;
            }
        }
        return true;
    }

    private static boolean module(boolean[][] modules, int index, int cursor, boolean horizontal) {
        return horizontal ? modules[index][cursor] : modules[cursor][index];
    }

    private static void setFunction(boolean[][] modules, boolean[][] reserved, int x, int y, boolean dark) {
        modules[y][x] = dark;
        reserved[y][x] = true;
    }

    private static void reserve(boolean[][] reserved, int x, int y) {
        if (x >= 0 && y >= 0 && x < reserved.length && y < reserved.length) {
            reserved[y][x] = true;
        }
    }

    private static byte[] reedSolomon(byte[] data, int ecCount) {
        var generator = rsGenerator(ecCount);
        var remainder = new int[ecCount];
        for (var value : data) {
            var factor = (value & 0xff) ^ remainder[0];
            System.arraycopy(remainder, 1, remainder, 0, ecCount - 1);
            remainder[ecCount - 1] = 0;
            if (factor != 0) {
                for (int index = 0; index < ecCount; index++) {
                    remainder[index] ^= gfMul(generator[index + 1], factor);
                }
            }
        }
        var result = new byte[ecCount];
        for (int index = 0; index < ecCount; index++) {
            result[index] = (byte) remainder[index];
        }
        return result;
    }

    private static int[] rsGenerator(int ecCount) {
        var generator = new int[] {1};
        for (int index = 0; index < ecCount; index++) {
            var next = new int[generator.length + 1];
            for (int item = 0; item < generator.length; item++) {
                next[item] ^= generator[item];
                next[item + 1] ^= gfMul(generator[item], GF_EXP[index]);
            }
            generator = next;
        }
        return generator;
    }

    private static int gfMul(int left, int right) {
        if (left == 0 || right == 0) {
            return 0;
        }
        return GF_EXP[GF_LOG[left] + GF_LOG[right]];
    }

    private static float positive(float value, String name) {
        if (!Float.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
        return value;
    }

    private static String number(float value) {
        return PdfNumbers.format(value);
    }

    private static final class BitBuffer {
        private int[] bits = new int[16];
        private int length;

        void append(int value, int count) {
            if (length + count > bits.length * 32) {
                bits = Arrays.copyOf(bits, bits.length * 2);
            }
            for (int index = count - 1; index >= 0; index--) {
                if (((value >>> index) & 1) != 0) {
                    bits[length >>> 5] |= 1 << (31 - (length & 31));
                }
                length++;
            }
        }

        int length() {
            return length;
        }

        byte[] toBytes() {
            var result = new byte[(length + 7) / 8];
            for (int index = 0; index < length; index++) {
                if (((bits[index >>> 5] >>> (31 - (index & 31))) & 1) != 0) {
                    result[index >>> 3] |= (byte) (0x80 >>> (index & 7));
                }
            }
            return result;
        }
    }
}
