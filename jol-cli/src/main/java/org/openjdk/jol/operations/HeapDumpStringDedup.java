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
import org.openjdk.jol.util.Multimap;
import org.openjdk.jol.util.Multiset;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.lang.System.out;

/**
 * @author Aleksey Shipilev
 */
public class HeapDumpStringDedup implements Operation {

    @Override
    public String label() {
        return "heapdump-stringdedup";
    }

    @Override
    public String description() {
        return "Read a heap dump and look for Strings that can be deduplicated";
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

        out.println();
        out.println("Discovering String objects...");
        StringVisitor sv = new StringVisitor();
        HeapDumpReader stringReader = new HeapDumpReader(new File(path), out, sv);
        stringReader.parse();

        out.println();
        out.println("Discovering String contents...");
        StringValueVisitor svv = new StringValueVisitor(sv.valuesToStrings());
        HeapDumpReader stringValueReader = new HeapDumpReader(new File(path), out, svv);
        stringValueReader.parse();

        out.println();
        out.println(layouter);
        out.println();

        out.println(svv.computeDuplicates(layouter));
        out.println();
    }

    public static class StringContents {
        private final int length;
        private final String componentType;
        private final byte[] contents;
        private final long hash;

        public StringContents(int length, String componentType, byte[] contents) {
            this.length = length;
            this.componentType = componentType;
            this.contents = Arrays.copyOf(contents, Math.min(contents.length, 32));
            this.hash = byteArrayHashCode(contents);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StringContents that = (StringContents) o;
            return length == that.length && hash == that.hash && componentType.equals(that.componentType);
        }

        @Override
        public int hashCode() {
            return (int) ((hash >> 32) ^ (hash));
        }

        public Object value() {
            if (componentType.equals("byte")) {
                // Boldly assume Latin1 encoding
                return new String(contents, StandardCharsets.ISO_8859_1);
            } else if (componentType.equals("char")) {
                return new String(contents, StandardCharsets.UTF_16);
            } else {
                return "N/A";
            }
        }
    }

    public static long byteArrayHashCode(byte[] src) {
        long result = 1;
        for (byte e : src) {
            result = 31 * result + e;
        }
        return result;
    }

    public static class StringVisitor extends HeapDumpReader.Visitor {
        private final Multimap<Long, Long> valuesToStrings = new Multimap<>();

        private long stringID;
        private int stringValueOffset;
        private int stringValueSize;

        @Override
        public void visitInstance(long id, long klassID, byte[] bytes, String name) {
            if (klassID == stringID) {
                ByteBuffer bb = ByteBuffer.wrap(bytes);
                long valueId;
                switch (stringValueSize) {
                    case 4:
                        valueId = bb.getInt(stringValueOffset);
                        break;
                    case 8:
                        valueId = bb.getLong(stringValueOffset);
                        break;
                    default:
                        throw new IllegalStateException("Cannot handle string value size: " + stringValueSize);
                }
                valuesToStrings.put(valueId, id);
            }
        }

        @Override
        public void visitClass(long id, String name, List<Integer> oopIdx, int oopSize) {
            if (name.equals("java.lang.String")) {
                stringID = id;
                if (oopIdx.size() == 1) {
                    stringValueOffset = oopIdx.get(0);
                    stringValueSize = oopSize;
                } else {
                    throw new IllegalStateException("String has more than one reference field");
                }
            }
        }

        public Multimap<Long, Long> valuesToStrings() {
            return valuesToStrings;
        }
    }

    public static class StringValueVisitor extends HeapDumpReader.Visitor {
        private final Multimap<Long, Long> valuesToStrings;
        private final Multiset<StringContents> contents = new Multiset<>();

        public StringValueVisitor(Multimap<Long, Long> valuesToStrings) {
            this.valuesToStrings = valuesToStrings;
        }

        @Override
        public void visitArray(long id, String componentType, int count, byte[] bytes) {
            if (valuesToStrings.contains(id)) {
                contents.add(new StringContents(count, componentType, bytes));
            }
        }

        public String computeDuplicates(Layouter layouter) {
            long stringSize = layouter.layout(ClassData.parseClass(String.class)).instanceSize();

            Map<Integer, Long> lenToSize = new HashMap<>();
            for (StringContents ba : contents.keys()) {
                ClassData cd = new ClassData(ba.componentType + "[]", ba.componentType, ba.length);
                lenToSize.put(ba.length, layouter.layout(cd).instanceSize());
            }

            List<StringContents> sorted = new ArrayList<>(contents.keys());
            sorted.sort((c1, c2) -> Long.compare(
                    (contents.count(c2) - 1) * lenToSize.get(c2.length),
                    (contents.count(c1) - 1) * lenToSize.get(c1.length))
            );


            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);

            pw.println("Duplicate Strings:");
            pw.println("  DUPS: Number of duplicated String instances");
            pw.println("  SIZE (V): Total size taken by duplicated String.value-s, amenable to GC dedup");
            pw.println("  SIZE (S+V): Total size taken by duplicated String, along with String objects");
            pw.println();

            pw.printf(" %15s %15s %15s   %s%n", "DUPS", "SIZE (V)", "SIZE (S+V)", "VALUE");
            pw.println("------------------------------------------------------------------------------------------------");

            long top = 30;
            long excessC = 0;
            long excessV = 0;
            long excessSV = 0;

            long topC = 0;
            long topV = 0;
            long topSV = 0;

            for (StringContents sc : sorted) {
                long count = contents.count(sc) - 1;
                long sizeV = lenToSize.get(sc.length);
                long sizeSV = sizeV + stringSize;

                if (count > 0) {
                    long sumV = count * sizeV;
                    long sumSV = count * sizeSV;
                    if (top-- > 0) {
                        pw.printf(" %,15d %,15d %,15d   %s%s%n",
                                count, sumV, sumSV,
                                sc.value(),
                                (sc.length > 32) ? "... (" + sc.length + " chars)" : "");
                        topC += count;
                        topV += sumV;
                        topSV += sumSV;
                    }
                    excessV += sumV;
                    excessSV += sumSV;
                    excessC += count;
                }
            }
            if (top <= 0) {
                pw.printf(" %,15d %,15d %,15d   %s%n", excessC - topC, excessV - topV, excessSV - topSV, "<other>");
            }
            pw.println("------------------------------------------------------------------------------------------------");
            pw.printf(" %,15d %,15d %,15d   %s%n", excessC, excessV, excessSV, "<total>");
            pw.println();

            pw.close();
            return sw.toString();
        }
    }

}
