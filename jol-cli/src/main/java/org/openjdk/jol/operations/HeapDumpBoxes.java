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
import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;
import org.openjdk.jol.layouters.HotSpotLayouter;
import org.openjdk.jol.layouters.Layouter;
import org.openjdk.jol.util.ClassUtils;
import org.openjdk.jol.util.Multiset;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

import static java.lang.System.out;

/**
 * @author Aleksey Shipilev
 */
public class HeapDumpBoxes implements Operation {

    static final Class<?>[] PRIMITIVE_CLASSES = {
            Boolean.class,
            Byte.class,
            Short.class,
            Character.class,
            Integer.class,
            Float.class,
            Long.class,
            Double.class
    };

    static final Class<?>[] PRIMITIVE_CACHE_CLASSES = {
            Short.class,
            Character.class,
            Integer.class,
            Float.class,
            Long.class,
            Double.class
    };

    @Override
    public String label() {
        return "heapdump-boxes";
    }

    @Override
    public String description() {
        return "Read a heap dump and look for data that looks duplicated, focusing on boxes";
    }

    private int getVMVersion() {
        try {
            return Integer.parseInt(System.getProperty("java.specification.version"));
        } catch (Exception e) {
            return 8;
        }
    }

    private long manualMarginalCost;
    private long arrayMarginalCost;

    public void computeMarginalCosts() {
        final int base = 1_234_567;
        final int size = 1_000_000;
        HashMap<Integer, Integer> empty = new HashMap<>();
        empty.put(base, base);
        empty.remove(base);

        HashMap<Integer, Integer> full = new HashMap<>();
        for (int i = base; i < base + size; i++) {
            full.put(i, i);
        }

        long intSize = ClassLayout.parseClass(Integer.class).instanceSize();
        long emptySize = GraphLayout.parseInstance(empty).totalSize();
        long fullSize = GraphLayout.parseInstance(full).totalSize();

        manualMarginalCost = (fullSize - emptySize) / size - intSize;

        long arraySize = ClassLayout.parseInstance(new Integer[size]).instanceSize();
        arrayMarginalCost = arraySize / size;
    }

    public void run(String... args) throws Exception {
        if (args.length == 0) {
            System.err.println("Expected a hprof file name.");
            return;
        }
        String path = args[0];

        Layouter layouter = new HotSpotLayouter(new ModelVM(), getVMVersion());

        computeMarginalCosts();

        out.println("Heap Dump: " + path);

        HeapDumpReader.MultiplexingVisitor mv = new HeapDumpReader.MultiplexingVisitor();

        Map<Class<?>, BoxVisitor> visitors = new HashMap<>();
        for (Class<?> cl : PRIMITIVE_CLASSES) {
            BoxVisitor v = new BoxVisitor(cl);
            visitors.put(cl, v);
            mv.add(v);
        }

        HeapDumpReader reader = new HeapDumpReader(new File(path), out, mv);
        reader.parse();

        out.println();
        out.println(layouter);
        out.println();

        StringWriter swVerbose = new StringWriter();
        StringWriter swAutoBox = new StringWriter();
        StringWriter swManual = new StringWriter();
        StringWriter swNull = new StringWriter();

        PrintWriter pwAutoBox = new PrintWriter(swAutoBox);
        PrintWriter pwManual = new PrintWriter(swManual);
        PrintWriter pwVerbose = new PrintWriter(swVerbose);
        PrintWriter pwNull = new PrintWriter(swNull);

        for (Class<?> cl : PRIMITIVE_CLASSES) {
            BoxVisitor v = visitors.get(cl);
            v.printOut(pwVerbose, pwNull, pwNull);
        }

        for (Class<?> cl : PRIMITIVE_CACHE_CLASSES) {
            BoxVisitor v = visitors.get(cl);
            v.printOut(pwNull, pwAutoBox, pwManual);
        }

        out.println(swVerbose);
        out.println(swAutoBox);
        out.println(swManual);
    }

    public class BoxVisitor extends HeapDumpReader.Visitor {
        private final Multiset<Number> values = new Multiset<>();
        private final String clName;
        private final Class<?> cl;

        public BoxVisitor(Class<?> cl) {
            this.clName = ClassUtils.humanReadableName(cl);
            this.cl = cl;
        }

        @Override
        public void visitInstance(long id, long klassID, byte[] bytes, String name) {
            if (name.equals(clName)) {
                switch (clName) {
                    case "java.lang.Byte":
                    case "java.lang.Boolean":
                        values.add(ByteBuffer.wrap(bytes).get());
                        break;
                    case "java.lang.Character":
                    case "java.lang.Short":
                        values.add(ByteBuffer.wrap(bytes).getShort());
                        break;
                    case "java.lang.Integer":
                        values.add(ByteBuffer.wrap(bytes).getInt());
                        break;
                    case "java.lang.Float":
                        values.add(ByteBuffer.wrap(bytes).getFloat());
                        break;
                    case "java.lang.Long":
                        values.add(ByteBuffer.wrap(bytes).getLong());
                        break;
                    case "java.lang.Double":
                        values.add(ByteBuffer.wrap(bytes).getDouble());
                        break;
                    default:
                        throw new IllegalStateException("Unknown class: " + clName);
                }
            }
        }

        public void printOut(PrintWriter verboseOut, PrintWriter autoboxOut, PrintWriter manualOut) {
            List<Number> sortedByValue = new ArrayList<>(values.keys());
            List<Number> sortedByCount = new ArrayList<>(values.keys());
            sortedByValue.sort(Comparator.comparing(Number::longValue));
            sortedByCount.sort((c1, c2) -> Long.compare(values.count(c2), values.count(c1)));

            long instanceSize = ClassLayout.parseClass(cl).instanceSize();

            verboseOut.println(clName + " boxes:");

            verboseOut.printf(" %13s %13s    %s%n", "DUPS", "SUM BYTES", "VALUE");
            verboseOut.println("------------------------------------------------------------------------------------------------");

            List<Integer> limits = new ArrayList<>();
            for (long i = 256; i <= 1024 * 1024 * 1024; i *= 2) {
                limits.add((int) i);
            }

            Multiset<Integer> autoBoxCountWins = new Multiset<>();
            Multiset<Integer> autoBoxSizeWins = new Multiset<>();

            for (Number v : sortedByValue) {
                long count = values.count(v) - 1;

                if (count > 0) {
                    long size = count * instanceSize;

                    verboseOut.printf(" %13d %13d    %s%n", count, size, v);

                    for (int limit : limits) {
                        if (-128 <= v.longValue() && v.longValue() < limit) {
                            autoBoxCountWins.add(limit, count);
                            autoBoxSizeWins.add(limit, size);
                        }
                    }
                }
            }
            verboseOut.println();

            if (cl.equals(Integer.class)) {
                autoboxOut.println(clName + ", savings with manual K[] cache, or non-default AutoBoxCacheMax:");
            } else {
                autoboxOut.println(clName + ", savings with manual K[] cache:");
            }
            autoboxOut.printf(" %20s %20s %20s%n", "CACHE SIZE", "SAVED INSTANCES", "SAVED BYTES");
            autoboxOut.println("------------------------------------------------------------------------------------------------");
            for (int limit : limits) {
                // Subtract the overhead for larger holding array
                long sizes = autoBoxSizeWins.count(limit) - arrayMarginalCost*(limit - 128);
                autoboxOut.printf(" %20d %20d %20s%n", limit, autoBoxCountWins.count(limit), sizes);
            }
            autoboxOut.println();

            Multiset<Integer> manualCachePopulation= new Multiset<>();
            Multiset<Integer> manualCacheCountWins = new Multiset<>();
            Multiset<Integer> manualCacheSizeWins = new Multiset<>();

            int n = 0;
            for (Number v : sortedByCount) {
                long count = values.count(v) - 1;

                if (count > 0) {
                    long size = count * instanceSize;

                    for (int limit : limits) {
                        if (n < limit) {
                            manualCacheCountWins.add(limit, count);
                            manualCacheSizeWins.add(limit, size);
                            manualCachePopulation.add(limit);
                        }
                    }
                    n++;
                }
            }

            manualOut.println(clName + ", savings with manual HashMap<K, K> cache:");
            manualOut.printf(" %20s %20s %20s%n", "CACHE SIZE", "SAVED INSTANCES", "SAVED BYTES");
            manualOut.println("------------------------------------------------------------------------------------------------");
            for (int limit : limits) {
                long sizes = manualCacheSizeWins.count(limit) - manualMarginalCost*manualCachePopulation.count(limit);
                manualOut.printf(" %20d %20d %20s%n", limit, manualCacheCountWins.count(limit), sizes);
            }
            manualOut.println();
        }

    }

}
