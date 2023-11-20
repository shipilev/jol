/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.jol.operations;

import org.openjdk.jol.Operation;
import org.openjdk.jol.datamodel.ModelVM;
import org.openjdk.jol.heap.HeapDumpReader;
import org.openjdk.jol.info.ClassData;
import org.openjdk.jol.layouters.HotSpotLayouter;
import org.openjdk.jol.layouters.Layouter;
import org.openjdk.jol.util.Multiset;

import java.io.*;
import java.util.*;

import static java.lang.System.out;

/**
 * @author Aleksey Shipilev
 */
public class HeapDumpDuplicates implements Operation {


    @Override
    public String label() {
        return "heapdump-duplicates";
    }

    @Override
    public String description() {
        return "Read a heap dump and look for data that looks duplicated";
    }

    private int getVMVersion() {
        try {
            return Integer.parseInt(System.getProperty("java.specification.version"));
        } catch (Exception e) {
            return 8;
        }
    }

    public void run(String... args) throws Exception {
        if (args.length == 0) {
            System.err.println("Expected a hprof file name.");
            return;
        }
        String path = args[0];

        Layouter layouter = new HotSpotLayouter(new ModelVM(), getVMVersion());

        out.println("Heap Dump: " + path);

        MultiplexingVisitor mv = new MultiplexingVisitor();

        InstanceVisitor iv = new InstanceVisitor();
        mv.add(iv);

        ArrayContentsVisitor av = new ArrayContentsVisitor();
        mv.add(av);

        HeapDumpReader reader = new HeapDumpReader(new File(path), out, mv);
        reader.parse();

        out.println();
        out.println(layouter);
        out.println();

        Map<String, Long> excesses = new HashMap<>();
        excesses.putAll(iv.compute(layouter));
        excesses.putAll(av.compute(layouter));

        List<String> sorted = new ArrayList<>(excesses.keySet());
        sorted.sort((c1, c2) -> Long.compare(excesses.get(c2), excesses.get(c1)));

        for (String s : sorted) {
            out.println(s);
        }
    }

    public static class MultiplexingVisitor implements HeapDumpReader.Visitor {
        private final List<HeapDumpReader.Visitor> visitors = new ArrayList<>();
        public void add(HeapDumpReader.Visitor v) {
            visitors.add(v);
        }

        @Override
        public void visitInstance(long id, long klassID, byte[] bytes, String name) {
            for (HeapDumpReader.Visitor v : visitors) {
                v.visitInstance(id, klassID, bytes, name);
            }
        }

        @Override
        public void visitClass(long id, String name, List<Integer> oopIdx, int oopSize) {
            for (HeapDumpReader.Visitor v : visitors) {
                v.visitClass(id, name, oopIdx, oopSize);
            }
        }

        @Override
        public void visitArray(long id, String componentType, int count, byte[] bytes) {
            for (HeapDumpReader.Visitor v : visitors) {
                v.visitArray(id, componentType, count, bytes);
            }
        }
    }

    public static class InstanceContents {
        private final long contents;
        private final boolean contentsIsHash;
        private final boolean contentsIsZero;
        private final byte contentsLen;

        public InstanceContents(byte[] contents) {
            if (contents.length <= 8) {
                this.contents = bytePrefixToLong(contents);
                this.contentsIsZero = byteArrayZero(contents);
                this.contentsIsHash = false;
                this.contentsLen = (byte) contents.length;
            } else {
                this.contents = byteArrayHashCode(contents);
                this.contentsIsZero = byteArrayZero(contents);
                this.contentsIsHash = true;
                this.contentsLen = -1;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InstanceContents that = (InstanceContents) o;
            return contents == that.contents && contentsIsHash == that.contentsIsHash;
        }

        @Override
        public int hashCode() {
            return (int) ((contents >> 32) ^ (contents));
        }

        public String value() {
            if (contentsIsHash) {
                if (contentsIsZero) {
                    return "{ 0 }";
                } else {
                    return "(hash: " + Long.toHexString(contents) + ")";
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("{ ");
            switch (contentsLen) {
                case 1:
                    sb.append(contents & 0xFFL);
                    break;
                case 2:
                    sb.append(contents & 0xFFFFL);
                    break;
                case 4:
                    sb.append(contents & 0xFFFF_FFFFL);
                    break;
                case 8:
                    sb.append(contents);
                    break;
            }
            sb.append(" }");

            return sb.toString();
        }
    }

    public static class HashedArrayContents {
        private final int length;
        private final String componentType;
        private final long contents;
        private final boolean contentsIsHash;
        private final boolean contentsIsZero;

        public HashedArrayContents(int length, String componentType, byte[] contents) {
            this.length = length;
            this.componentType = componentType;
            if (contents.length <= 8) {
                this.contents = bytePrefixToLong(contents);
                this.contentsIsHash = false;
                this.contentsIsZero = byteArrayZero(contents);
            } else {
                this.contents = byteArrayHashCode(contents);
                this.contentsIsHash = true;
                this.contentsIsZero = byteArrayZero(contents);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HashedArrayContents that = (HashedArrayContents) o;
            return length == that.length && contents == that.contents && contentsIsHash == that.contentsIsHash && componentType.equals(that.componentType);
        }

        @Override
        public int hashCode() {
            return (int) ((contents >> 32) ^ (contents));
        }

        private int unitSize() {
            switch (componentType) {
                case "boolean":
                case "byte":
                    return 1;
                case "short":
                case "char":
                    return 2;
                case "int":
                case "float":
                    return 4;
                case "double":
                case "long":
                    return 8;
                default:
                    return 4;
            }
        }

        public String value() {
            if (contentsIsHash) {
                if (contentsIsZero) {
                    return "{ 0, ..., 0 }";
                } else {
                    return "(hash: " + Long.toHexString(contents) + ")";
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("{ ");
            switch (unitSize()) {
                case 1:
                    switch (length) {
                        case 8:
                            sb.append((contents >> 56) & 0xFF);
                            sb.append(", ");
                            sb.append((contents >> 48) & 0xFF);
                            sb.append(", ");
                            sb.append((contents >> 40) & 0xFF);
                            sb.append(", ");
                            sb.append((contents >> 32) & 0xFF);
                            sb.append(", ");
                        case 4:
                            sb.append((contents >> 24) & 0xFF);
                            sb.append(", ");
                            sb.append((contents >> 16) & 0xFF);
                            sb.append(", ");
                        case 2:
                            sb.append((contents >> 8) & 0xFF);
                            sb.append(", ");
                        case 1:
                            sb.append((contents >> 0) & 0xFF);
                    }
                    break;
                case 2:
                    switch (length) {
                        case 4:
                            sb.append((contents >> 48) & 0xFFFF);
                            sb.append(", ");
                            sb.append((contents >> 32) & 0xFFFF);
                            sb.append(", ");
                        case 2:
                            sb.append((contents >> 16) & 0xFFFF);
                            sb.append(", ");
                        case 1:
                            sb.append((contents >> 0) & 0xFFFF);
                    }
                    break;
                case 4:
                    switch (length) {
                        case 2:
                            sb.append((contents >> 32) & 0xFFFF_FFFFL);
                            sb.append(", ");
                        case 1:
                            sb.append((contents >> 0) & 0xFFFF_FFFFL);
                    }
                    break;
                case 8:
                    sb.append(contents);
                    break;
            }
            sb.append(" }");

            return sb.toString();
        }
    }

    private static long bytePrefixToLong(byte[] src) {
        int limit = Math.min(src.length, 8);
        long res = 0;
        for (int c = 0; c < limit; c++) {
            res = (res << 8) + (src[c] & 0xFF);
        }
        return res;
    }

    public static long byteArrayHashCode(byte[] src) {
        long result = 1;
        for (byte e : src) {
            result = 31 * result + e;
        }
        return result;
    }

    public static boolean byteArrayZero(byte[] src) {
        for (byte e : src) {
            if (e != 0) {
                return false;
            }
        }
        return true;
    }

    public static class InstanceVisitor implements HeapDumpReader.Visitor {
        private final Map<ClassData, Multiset<InstanceContents>> contents = new HashMap<>();

        @Override
        public void visitInstance(long id, long klassID, byte[] bytes, String name) {
            Multiset<InstanceContents> conts = contents.get(name);
            if (conts == null) {
                conts = new Multiset<>();
                // FIXME:
//                contents.put(name, conts);
            } else {
                conts.pruneForSize(1_000_000);
            }
            conts.add(new InstanceContents(bytes));
        }

        @Override
        public void visitClass(long id, String name, List<Integer> oopIdx, int oopSize) {
            // Do nothing
        }

        @Override
        public void visitArray(long id, String componentType, int count, byte[] bytes) {
            // Do nothing
        }

        public Map<String, Long> compute(Layouter layouter) {
            Map<String, Long> excesses = new HashMap<>();
            for (ClassData cd : contents.keySet()) {
                Multiset<InstanceContents> ics = contents.get(cd);

                boolean hasExcess = false;
                for (InstanceContents ba : ics.keys()) {
                    long count = ics.count(ba);
                    if (count > 1) {
                        hasExcess = true;
                        break;
                    }
                }

                if (!hasExcess) {
                    continue;
                }

                long intSize = layouter.layout(cd).instanceSize();

                List<InstanceContents> sorted = new ArrayList<>(ics.keys());
                sorted.sort((c1, c2) -> Long.compare(ics.count(c2), ics.count(c1)));

                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                pw.println(cd.name() + " potential duplicates:");

                pw.printf(" %13s %13s   %s%n", "DUPS", "SUM SIZE", "VALUE");
                pw.println("------------------------------------------------------------------------------------------------");

                int top = 30;
                long excess = 0;
                for (InstanceContents ba : sorted) {
                    long count = ics.count(ba);
                    if (count > 1) {
                        long size = (count - 1) * intSize;
                        excess += size;
                        if (top-- > 0) {
                            pw.printf(" %13d %13d   %s%n", count - 1, size, ba.value());
                        }
                    }
                }

                if (top <= 0) {
                    pw.printf(" %13s %13s   %s%n", "", "", ".........");
                }
                pw.printf(" %13s %13d   %s%n", "", excess, "<total>");
                pw.println();

                pw.close();

                excesses.put(sw.toString(), excess);
            }
            return excesses;
        }
    }

    public static class ArrayContentsVisitor implements HeapDumpReader.Visitor {
        private final Map<String, Multiset<HashedArrayContents>> arrayContents = new HashMap<>();

        @Override
        public void visitInstance(long id, long klassID, byte[] bytes, String name) {
            // Do nothing
        }

        @Override
        public void visitClass(long id, String name, List<Integer> oopIdx, int oopSize) {
            // Do nothing
        }

        @Override
        public void visitArray(long id, String componentType, int count, byte[] bytes) {
            Multiset<HashedArrayContents> conts = arrayContents.get(componentType);
            if (conts == null) {
                conts = new Multiset<>();
                arrayContents.put(componentType, conts);
            } else {
                conts.pruneForSize(1_000_000);
            }
            conts.add(new HashedArrayContents(count, componentType, bytes));
        }

        public Map<String, Long> compute(Layouter layouter) {
            Map<String, Long> excesses = new HashMap<>();
            for (String componentType : arrayContents.keySet()) {
                Multiset<HashedArrayContents> ics = arrayContents.get(componentType);

                boolean hasExcess = false;
                for (HashedArrayContents ba : ics.keys()) {
                    long count = ics.count(ba);
                    if (count > 1) {
                        hasExcess = true;
                        break;
                    }
                }

                if (!hasExcess) {
                    continue;
                }

                Map<Integer, Long> lenToSize = new HashMap<>();
                for (HashedArrayContents ba : ics.keys()) {
                    ClassData cd = new ClassData(componentType + "[]", componentType, ba.length);
                    lenToSize.put(ba.length, layouter.layout(cd).instanceSize());
                }

                List<HashedArrayContents> sorted = new ArrayList<>(ics.keys());
                sorted.sort((c1, c2) -> Long.compare(
                        (ics.count(c2) - 1) * lenToSize.get(c2.length),
                        (ics.count(c1) - 1) * lenToSize.get(c1.length))
                );

                long top = 30;
                long excess = 0;

                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);

                pw.println(componentType + "[] potential duplicates:");

                pw.printf(" %13s %13s   %s%n", "DUPS", "SUM SIZE", "VALUE");
                pw.println("------------------------------------------------------------------------------------------------");

                for (HashedArrayContents ba : sorted) {
                    long count = ics.count(ba);
                    long intSize = lenToSize.get(ba.length);

                    if (count > 1) {
                        long sumSize = (count - 1) * intSize;
                        if (top-- > 0) {
                            pw.printf(" %13d %13d   %s[%d] %s%n", count, sumSize, componentType, ba.length, ba.value());
                        }
                        excess += sumSize;
                    }
                }

                if (top <= 0) {
                    pw.printf(" %13s %13s   %s%n", "", "", ".........");
                }
                pw.printf(" %13s %13d   %s%n", "", excess, "<total>");
                pw.println();

                pw.close();

                excesses.put(sw.toString(), excess);
            }
            return excesses;
        }

    }

}
