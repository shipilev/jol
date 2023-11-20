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
package org.openjdk.jol.util;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ASCIITable {

    private final int top;
    private final String header;
    private final int numberColumns;
    private final String[] columns;

    private final List<Line> lines;

    public ASCIITable(int top, String header, String... columns) {
        this.top = top;
        this.header = header;
        this.columns = columns;
        this.numberColumns = columns.length - 1;
        this.lines = new ArrayList<>();
    }

    private static class Line {
        private final Long[] numbers;
        private final String value;

        public Line(String value, Long[] numbers) {
            this.numbers = numbers;
            this.value = value;
        }
    }

    public void addLine(String value, Long... numbers) {
        lines.add(new Line(value, numbers));
    }

    public void printRevSorted(PrintStream ps, int column) {
        PrintWriter pw = new PrintWriter(ps);
        printRevSorted(pw, column);
        pw.flush();
    }

    public void printRevSorted(PrintWriter pw, int sortColumn) {
        lines.sort(Comparator.comparing((Line l) -> l.numbers[sortColumn]).reversed());

        pw.println(header);
        pw.println();

        pw.println("Table is sorted by \"" + columns[sortColumn] + "\"");
        pw.println();

        for (int c = 0; c < numberColumns; c++) {
            pw.printf(" %15s", columns[c]);
        }
        pw.println("    " + columns[numberColumns]);
        pw.println("------------------------------------------------------------------------------------------------");


        long[] tops = new long[numberColumns];
        long[] sums = new long[numberColumns];

        int current = 0;

        for (Line l : lines) {
            if (current < top) {
                for (int c = 0; c < numberColumns; c++) {
                    pw.printf(" %,15d", l.numbers[c]);
                    tops[c] += l.numbers[c];
                    sums[c] += l.numbers[c];
                }
                pw.printf("    %s%n", l.value);
            } else {
                for (int c = 0; c < numberColumns; c++) {
                    sums[c] += l.numbers[c];
                }
            }
            current++;
        }

        if (current > top) {
            for (int c = 0; c < numberColumns; c++) {
                pw.printf(" %15s", "...");
            }
            pw.printf("    %s%n", "...");
            for (int c = 0; c < numberColumns; c++) {
                pw.printf(" %,15d", sums[c] - tops[c]);
            }
            pw.printf("    %s%n", "<other>");
        }
        pw.println("------------------------------------------------------------------------------------------------");
        for (int c = 0; c < numberColumns; c++) {
            pw.printf(" %,15d", sums[c]);
        }
        pw.printf("    %s%n", "<total>");
        pw.println();
    }

}
