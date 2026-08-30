package quiz.web;

final class QrCode {


    private static final int[][] ECC_M = {
            {},
            {10, 1, 16},
            {16, 1, 28},
            {26, 1, 44},
            {18, 2, 32},
            {24, 2, 43},
            {16, 4, 27},
    };


    private static final int[] ALIGN = {0, 0, 18, 22, 26, 30, 34};

    private final int size;
    private final boolean[][] dark;

    private QrCode(int size, boolean[][] dark) {
        this.size = size;
        this.dark = dark;
    }




    static QrCode encode(String text) {
        byte[] data = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        int version = -1;
        for (int v = 1; v <= 6; v++) {
            if (4 + 8 + data.length * 8 <= totalDataCodewords(v) * 8) {
                version = v;
                break;
            }
        }
        if (version < 0) {
            throw new IllegalArgumentException("Metin QR koda sığmıyor: " + data.length + " bayt");
        }

        byte[] codewords = buildCodewords(data, version);
        int size = 17 + 4 * version;

        boolean[][] modules = new boolean[size][size];
        boolean[][] reserved = new boolean[size][size];
        drawFunctionPatterns(modules, reserved, version, size);
        placeData(modules, reserved, codewords, size);


        int bestMask = 0;
        int bestPenalty = Integer.MAX_VALUE;
        boolean[][] best = null;
        for (int mask = 0; mask < 8; mask++) {
            boolean[][] candidate = copy(modules, size);
            applyMask(candidate, reserved, mask, size);
            drawFormatInfo(candidate, mask, size);
            int penalty = penalty(candidate, size);
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                bestMask = mask;
                best = candidate;
            }
        }
        if (best == null) {
            throw new IllegalStateException("Maske seçilemedi");
        }
        return new QrCode(size, best);
    }


    String toSvg(int pixelSize, String darkColor, String lightColor) {
        int quiet = 4;
        int total = size + quiet * 2;

        StringBuilder path = new StringBuilder();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (dark[y][x]) {
                    path.append("M").append(x + quiet).append(",").append(y + quiet).append("h1v1h-1z");
                }
            }
        }

        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + pixelSize
                + "\" height=\"" + pixelSize + "\" viewBox=\"0 0 " + total + " " + total
                + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"Katılım bağlantısı\">"
                + "<rect width=\"" + total + "\" height=\"" + total + "\" fill=\"" + lightColor + "\"/>"
                + "<path d=\"" + path + "\" fill=\"" + darkColor + "\"/></svg>";
    }



    private static int totalDataCodewords(int version) {
        return ECC_M[version][1] * ECC_M[version][2];
    }


    private static byte[] buildCodewords(byte[] data, int version) {
        int totalData = totalDataCodewords(version);
        BitBuffer bits = new BitBuffer();

        bits.append(0b0100, 4);
        bits.append(data.length, 8);
        for (byte b : data) {
            bits.append(b & 0xFF, 8);
        }


        int capacity = totalData * 8;
        for (int i = 0; i < 4 && bits.size() < capacity; i++) {
            bits.append(0, 1);
        }
        while (bits.size() % 8 != 0) {
            bits.append(0, 1);
        }

        boolean useEc = true;
        while (bits.size() < capacity) {
            bits.append(useEc ? 0xEC : 0x11, 8);
            useEc = !useEc;
        }

        byte[] dataCodewords = bits.toBytes();

        int eccPerBlock = ECC_M[version][0];
        int blockCount = ECC_M[version][1];
        int perBlock = ECC_M[version][2];

        byte[][] dataBlocks = new byte[blockCount][];
        byte[][] eccBlocks = new byte[blockCount][];
        for (int i = 0; i < blockCount; i++) {
            dataBlocks[i] = new byte[perBlock];
            System.arraycopy(dataCodewords, i * perBlock, dataBlocks[i], 0, perBlock);
            eccBlocks[i] = reedSolomon(dataBlocks[i], eccPerBlock);
        }


        byte[] result = new byte[blockCount * (perBlock + eccPerBlock)];
        int pos = 0;
        for (int i = 0; i < perBlock; i++) {
            for (byte[] block : dataBlocks) {
                result[pos++] = block[i];
            }
        }
        for (int i = 0; i < eccPerBlock; i++) {
            for (byte[] block : eccBlocks) {
                result[pos++] = block[i];
            }
        }
        return result;
    }



    private static final int[] EXP = new int[512];
    private static final int[] LOG = new int[256];

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            EXP[i] = x;
            LOG[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0) {
                x ^= 0x11D;
            }
        }
        for (int i = 255; i < 512; i++) {
            EXP[i] = EXP[i - 255];
        }
    }

    private static int mul(int a, int b) {
        return (a == 0 || b == 0) ? 0 : EXP[LOG[a] + LOG[b]];
    }

    private static byte[] reedSolomon(byte[] data, int eccLength) {
        int[] generator = new int[eccLength + 1];
        generator[0] = 1;
        for (int i = 0; i < eccLength; i++) {
            for (int j = i + 1; j > 0; j--) {
                generator[j] = generator[j - 1] ^ mul(generator[j], EXP[i]);
            }
            generator[0] = mul(generator[0], EXP[i]);
        }

        int[] remainder = new int[eccLength];
        for (byte b : data) {
            int factor = (b & 0xFF) ^ remainder[0];
            System.arraycopy(remainder, 1, remainder, 0, eccLength - 1);
            remainder[eccLength - 1] = 0;
            for (int i = 0; i < eccLength; i++) {
                remainder[i] ^= mul(generator[eccLength - 1 - i], factor);
            }
        }

        byte[] out = new byte[eccLength];
        for (int i = 0; i < eccLength; i++) {
            out[i] = (byte) remainder[i];
        }
        return out;
    }



    private static void drawFunctionPatterns(boolean[][] m, boolean[][] r, int version, int size) {

        drawFinder(m, r, 0, 0, size);
        drawFinder(m, r, size - 7, 0, size);
        drawFinder(m, r, 0, size - 7, size);


        for (int i = 8; i < size - 8; i++) {
            boolean on = i % 2 == 0;
            m[6][i] = on; r[6][i] = true;
            m[i][6] = on; r[i][6] = true;
        }


        if (version >= 2) {
            int c = ALIGN[version];
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    int max = Math.max(Math.abs(dx), Math.abs(dy));
                    m[c + dy][c + dx] = max != 1;
                    r[c + dy][c + dx] = true;
                }
            }
        }


        for (int i = 0; i < 9; i++) {
            r[8][i] = true;
            r[i][8] = true;
        }
        for (int i = 0; i < 8; i++) {
            r[8][size - 1 - i] = true;
            r[size - 1 - i][8] = true;
        }


        m[size - 8][8] = true;
        r[size - 8][8] = true;
    }

    private static void drawFinder(boolean[][] m, boolean[][] r, int left, int top, int size) {
        for (int dy = -1; dy <= 7; dy++) {
            for (int dx = -1; dx <= 7; dx++) {
                int y = top + dy, x = left + dx;
                if (y < 0 || y >= size || x < 0 || x >= size) {
                    continue;
                }
                boolean on = dx >= 0 && dx <= 6 && dy >= 0 && dy <= 6
                        && (dx == 0 || dx == 6 || dy == 0 || dy == 6
                            || (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4));
                m[y][x] = on;
                r[y][x] = true;
            }
        }
    }


    private static void placeData(boolean[][] m, boolean[][] r, byte[] codewords, int size) {
        int bitIndex = 0;
        boolean upward = true;

        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) {
                right = 5;
            }
            for (int step = 0; step < size; step++) {
                int y = upward ? size - 1 - step : step;
                for (int col = 0; col < 2; col++) {
                    int x = right - col;
                    if (r[y][x]) {
                        continue;
                    }
                    boolean bit = false;
                    if (bitIndex < codewords.length * 8) {
                        bit = ((codewords[bitIndex >> 3] >> (7 - (bitIndex & 7))) & 1) == 1;
                    }
                    m[y][x] = bit;
                    bitIndex++;
                }
            }
            upward = !upward;
        }
    }

    private static void applyMask(boolean[][] m, boolean[][] r, int mask, int size) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (r[y][x]) {
                    continue;
                }
                boolean flip = switch (mask) {
                    case 0 -> (y + x) % 2 == 0;
                    case 1 -> y % 2 == 0;
                    case 2 -> x % 3 == 0;
                    case 3 -> (y + x) % 3 == 0;
                    case 4 -> (y / 2 + x / 3) % 2 == 0;
                    case 5 -> (y * x) % 2 + (y * x) % 3 == 0;
                    case 6 -> ((y * x) % 2 + (y * x) % 3) % 2 == 0;
                    default -> ((y + x) % 2 + (y * x) % 3) % 2 == 0;
                };
                if (flip) {
                    m[y][x] = !m[y][x];
                }
            }
        }
    }


    private static void drawFormatInfo(boolean[][] m, int mask, int size) {
        int data = (0b00 << 3) | mask;
        int rem = data;
        for (int i = 0; i < 10; i++) {
            rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        }
        int bits = ((data << 10) | rem) ^ 0x5412;



        for (int i = 0; i <= 5; i++) {
            m[i][8] = getBit(bits, i);
        }
        m[7][8] = getBit(bits, 6);
        m[8][8] = getBit(bits, 7);
        m[8][7] = getBit(bits, 8);
        for (int i = 9; i < 15; i++) {
            m[8][14 - i] = getBit(bits, i);
        }
        for (int i = 0; i < 8; i++) {
            m[8][size - 1 - i] = getBit(bits, i);
        }
        for (int i = 8; i < 15; i++) {
            m[size - 15 + i][8] = getBit(bits, i);
        }
        m[size - 8][8] = true;
    }

    private static boolean getBit(int value, int index) {
        return ((value >>> index) & 1) == 1;
    }



    private static int penalty(boolean[][] m, int size) {
        int score = 0;


        for (int i = 0; i < size; i++) {
            score += lineRun(m, size, i, true);
            score += lineRun(m, size, i, false);
        }


        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean c = m[y][x];
                if (c == m[y][x + 1] && c == m[y + 1][x] && c == m[y + 1][x + 1]) {
                    score += 3;
                }
            }
        }


        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (x + 6 < size && matchesFinderLike(m, y, x, true)) score += 40;
                if (y + 6 < size && matchesFinderLike(m, y, x, false)) score += 40;
            }
        }


        int darkCount = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (m[y][x]) darkCount++;
            }
        }
        int percent = darkCount * 100 / (size * size);
        score += Math.abs(percent - 50) / 5 * 10;

        return score;
    }

    private static int lineRun(boolean[][] m, int size, int index, boolean horizontal) {
        int score = 0, run = 1;
        boolean previous = horizontal ? m[index][0] : m[0][index];
        for (int i = 1; i < size; i++) {
            boolean current = horizontal ? m[index][i] : m[i][index];
            if (current == previous) {
                run++;
            } else {
                if (run >= 5) score += 3 + (run - 5);
                previous = current;
                run = 1;
            }
        }
        if (run >= 5) score += 3 + (run - 5);
        return score;
    }

    private static boolean matchesFinderLike(boolean[][] m, int y, int x, boolean horizontal) {
        boolean[] pattern = {true, false, true, true, true, false, true};
        for (int i = 0; i < 7; i++) {
            boolean cell = horizontal ? m[y][x + i] : m[y + i][x];
            if (cell != pattern[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean[][] copy(boolean[][] source, int size) {
        boolean[][] out = new boolean[size][size];
        for (int i = 0; i < size; i++) {
            System.arraycopy(source[i], 0, out[i], 0, size);
        }
        return out;
    }


    private static final class BitBuffer {
        private final java.util.List<Boolean> bits = new java.util.ArrayList<>();

        void append(int value, int length) {
            for (int i = length - 1; i >= 0; i--) {
                bits.add(((value >>> i) & 1) == 1);
            }
        }

        int size() {
            return bits.size();
        }

        byte[] toBytes() {
            byte[] out = new byte[bits.size() / 8];
            for (int i = 0; i < bits.size(); i++) {
                if (bits.get(i)) {
                    out[i >> 3] |= (byte) (1 << (7 - (i & 7)));
                }
            }
            return out;
        }
    }
}
