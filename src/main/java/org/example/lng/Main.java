package org.example.lng;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public final class Main {
    private static final int IO_BUFFER_SIZE = 1 << 20;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java -jar lng-test.jar <absolute-path-to-input-file>");
            System.exit(2);
        }

        final long startNs = System.nanoTime();
        final Path input = Path.of(args[0]);
        final Path output = Path.of("groups.txt");

        final ArrayList<String> uniqueLines = new ArrayList<>(1 << 20);
        final HashMap<String, Integer> lineToId = new HashMap<>(1 << 20, 0.75f);

        final DSU dsu = new DSU(1 << 20);
        final ArrayList<HashMap<String, Integer>> colToValueOwner = new ArrayList<>(32);

        long totalRead = 0;
        long totalInvalid = 0;
        long totalDuplicate = 0;

        try (InputStream raw = Files.newInputStream(input);
             BufferedInputStream in = new BufferedInputStream(raw, IO_BUFFER_SIZE)) {
            final FastLineReader reader = new FastLineReader(in);
            String line;
            while ((line = reader.readLine()) != null) {
                totalRead++;
                if (line.isEmpty()) {
                    totalInvalid++;
                    continue;
                }

                final ParsedLine parsed = ParsedLine.tryParse(line);
                if (parsed == null) {
                    totalInvalid++;
                    continue;
                }

                Integer existing = lineToId.putIfAbsent(parsed.originalLine, uniqueLines.size());
                if (existing != null) {
                    totalDuplicate++;
                    continue;
                }

                final int id = uniqueLines.size();
                uniqueLines.add(parsed.originalLine);
                dsu.ensureCapacity(id + 1);

                final String[] fields = parsed.fields;
                for (int col = 0; col < fields.length; col++) {
                    final String v = fields[col];
                    if (v.isEmpty()) continue;
                    while (colToValueOwner.size() <= col) {
                        colToValueOwner.add(new HashMap<>(1 << 16, 0.75f));
                    }
                    final HashMap<String, Integer> m = colToValueOwner.get(col);
                    final Integer other = m.putIfAbsent(v, id);
                    if (other != null) {
                        dsu.union(id, other);
                    }
                }
            }
        }

        final int n = uniqueLines.size();
        final int[] root = new int[n];
        final int[] size = new int[n];
        for (int i = 0; i < n; i++) {
            int r = dsu.find(i);
            root[i] = r;
            size[r]++;
        }

        final int[] offsets = new int[n + 1];
        for (int r = 0; r < n; r++) offsets[r + 1] = offsets[r] + size[r];
        final int[] cursor = Arrays.copyOf(offsets, offsets.length);
        final int[] members = new int[n];
        for (int i = 0; i < n; i++) {
            int r = root[i];
            members[cursor[r]++] = i;
        }

        int groupsGt1 = 0;
        int groupCount = 0;
        for (int r = 0; r < n; r++) {
            if (size[r] == 0) continue;
            groupCount++;
            if (size[r] > 1) groupsGt1++;
        }

        final int[] roots = new int[groupsGt1];
        int k = 0;
        for (int r = 0; r < n; r++) {
            if (size[r] > 1) roots[k++] = r;
        }
        sortRootsBySizeDesc(roots, size);

        try (BufferedWriter w = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            w.write(Integer.toString(groupsGt1));
            w.newLine();
            w.newLine();

            int groupIndex = 1;
            for (int r : roots) {
                w.write("Группа ");
                w.write(Integer.toString(groupIndex++));
                w.newLine();
                int from = offsets[r];
                int to = offsets[r] + size[r];
                for (int p = from; p < to; p++) {
                    int id = members[p];
                    w.write(uniqueLines.get(id));
                    w.newLine();
                }
                w.newLine();
            }
        }

        final long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        System.out.println("groups_gt_1=" + groupsGt1);
        System.out.println("time_ms=" + elapsedMs);
        System.out.println("unique_lines=" + n);
        System.out.println("total_read=" + totalRead);
        System.out.println("invalid_skipped=" + totalInvalid);
        System.out.println("duplicates_skipped=" + totalDuplicate);
        System.out.println("output=" + output.toAbsolutePath());
        System.out.println("total_groups(all_sizes)=" + groupCount);
    }

    private static void sortRootsBySizeDesc(int[] roots, int[] size) {
        if (roots.length <= 1) return;
        int[] stack = new int[Math.max(64, roots.length * 2)];
        int sp = 0;
        stack[sp++] = 0;
        stack[sp++] = roots.length - 1;

        while (sp > 0) {
            int hi = stack[--sp];
            int lo = stack[--sp];
            if (lo >= hi) continue;

            int i = lo, j = hi;
            int pivot = roots[lo + ((hi - lo) >>> 1)];
            int pivotSize = size[pivot];

            while (i <= j) {
                while (size[roots[i]] > pivotSize) i++;
                while (size[roots[j]] < pivotSize) j--;
                if (i <= j) {
                    int tmp = roots[i];
                    roots[i] = roots[j];
                    roots[j] = tmp;
                    i++;
                    j--;
                }
            }

            int leftLo = lo, leftHi = j;
            int rightLo = i, rightHi = hi;
            int leftLen = leftHi - leftLo;
            int rightLen = rightHi - rightLo;
            if (leftLen > rightLen) {
                if (leftLo < leftHi) {
                    stack[sp++] = leftLo;
                    stack[sp++] = leftHi;
                }
                if (rightLo < rightHi) {
                    stack[sp++] = rightLo;
                    stack[sp++] = rightHi;
                }
            } else {
                if (rightLo < rightHi) {
                    stack[sp++] = rightLo;
                    stack[sp++] = rightHi;
                }
                if (leftLo < leftHi) {
                    stack[sp++] = leftLo;
                    stack[sp++] = leftHi;
                }
            }
        }
    }

    private static final class ParsedLine {
        final String originalLine;
        final String[] fields;

        private ParsedLine(String originalLine, String[] fields) {
            this.originalLine = originalLine;
            this.fields = fields;
        }

        static ParsedLine tryParse(String line) {
            int len = line.length();
            int quoteCount = 0;
            for (int i = 0; i < len; i++) {
                if (line.charAt(i) == '"') quoteCount++;
            }
            if ((quoteCount & 1) == 1) return null;

            ArrayList<String> out = new ArrayList<>(8);
            StringBuilder sb = new StringBuilder(64);

            boolean inQuotes = false;
            boolean tokenStartedWithQuote = false;
            boolean tokenHasQuote = false;

            for (int i = 0; i < len; i++) {
                char c = line.charAt(i);
                if (c == '"') {
                    tokenHasQuote = true;
                    if (!inQuotes) {
                        if (sb.length() != 0) return null;
                        inQuotes = true;
                        tokenStartedWithQuote = true;
                    } else {
                        inQuotes = false;
                        if (i + 1 < len && line.charAt(i + 1) != ';') return null;
                    }
                    continue;
                }

                if (c == ';' && !inQuotes) {
                    out.add(finishToken(sb, tokenStartedWithQuote, tokenHasQuote));
                    sb.setLength(0);
                    tokenStartedWithQuote = false;
                    tokenHasQuote = false;
                    continue;
                }

                sb.append(c);
            }

            if (inQuotes) return null;
            out.add(finishToken(sb, tokenStartedWithQuote, tokenHasQuote));

            return new ParsedLine(line, out.toArray(String[]::new));
        }

        private static String finishToken(StringBuilder sb, boolean startedWithQuote, boolean tokenHasQuote) {
            if (!startedWithQuote && tokenHasQuote) {
                return "";
            }
            return sb.toString();
        }
    }

    private static final class DSU {
        private int[] parent;
        private byte[] rank;

        DSU(int initialCapacity) {
            parent = new int[Math.max(16, initialCapacity)];
            rank = new byte[parent.length];
            for (int i = 0; i < parent.length; i++) parent[i] = i;
        }

        void ensureCapacity(int n) {
            if (n <= parent.length) return;
            final int oldCap = parent.length;
            int newCap = parent.length;
            while (newCap < n) newCap = newCap + (newCap >>> 1) + 16;
            parent = Arrays.copyOf(parent, newCap);
            rank = Arrays.copyOf(rank, newCap);
            for (int i = oldCap; i < newCap; i++) {
                parent[i] = i;
                rank[i] = 0;
            }
        }

        int find(int x) {
            int p = parent[x];
            if (p == x) return x;
            int r = find(p);
            parent[x] = r;
            return r;
        }

        void union(int a, int b) {
            int ra = find(a);
            int rb = find(b);
            if (ra == rb) return;
            byte rka = rank[ra];
            byte rkb = rank[rb];
            if (rka < rkb) {
                parent[ra] = rb;
            } else if (rka > rkb) {
                parent[rb] = ra;
            } else {
                parent[rb] = ra;
                rank[ra] = (byte) (rka + 1);
            }
        }
    }

    private static final class FastLineReader {
        private final InputStream in;
        private final byte[] buf;
        private int pos;
        private int lim;

        FastLineReader(InputStream in) {
            this.in = in;
            this.buf = new byte[IO_BUFFER_SIZE];
        }

        String readLine() throws IOException {
            StringBuilder sb = null;
            while (true) {
                if (pos >= lim) {
                    lim = in.read(buf);
                    pos = 0;
                    if (lim < 0) {
                        return sb == null ? null : sb.toString();
                    }
                }

                int start = pos;
                while (pos < lim) {
                    byte b = buf[pos++];
                    if (b == '\n') {
                        int end = pos - 1;
                        if (end > start && buf[end - 1] == '\r') end--;
                        if (sb == null) {
                            return new String(buf, start, end - start, StandardCharsets.UTF_8);
                        }
                        sb.append(new String(buf, start, end - start, StandardCharsets.UTF_8));
                        return sb.toString();
                    }
                }

                int end = pos;
                if (sb == null) sb = new StringBuilder(128);
                sb.append(new String(buf, start, end - start, StandardCharsets.UTF_8));
            }
        }
    }
}

