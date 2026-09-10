/*
 * SonarQube Apple Plugin - Enables analysis of Swift and Objective-C projects into SonarQube.
 * Copyright © 2022 inside|app (contact@insideapp.fr)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.insideapp.sonarqube.apple.commons;

import java.util.Arrays;

/**
 * A single source line of a file.
 * <p>
 * {@code start}, {@code end} and {@code count} are expressed in <b>Unicode code points</b>, so that they can be
 * compared directly with the token indexes reported by ANTLR: {@code CharStreams.fromStream(...)} builds a
 * {@code CodePointCharStream}, whose indexes count code points and not UTF-16 code units.
 * <p>
 * SonarQube, on the other hand, addresses a position inside a line with a <b>UTF-16 code unit</b> offset (it holds
 * the file contents in a Java {@code String}). Both units only differ on lines holding supplementary characters
 * (emojis, for instance), which is why the offsets of those characters are kept here, see
 * {@link #toUtf16Column(int)}.
 */
public final class SourceLine {

    private static final int[] NO_SUPPLEMENTARY = new int[0];

    private final int count;
    private final int start;
    private final int end;
    private final int line;

    /**
     * Code point offsets, relative to the beginning of the line, of the supplementary characters it holds.
     * Sorted, and empty for the vast majority of the lines.
     */
    private final int[] supplementaryOffsets;

    public SourceLine(final int line, final int count, final int start, final int end) {
        this(line, count, start, end, NO_SUPPLEMENTARY);
    }

    public SourceLine(final int line, final int count, final int start, final int end,
                      final int[] supplementaryOffsets) {
        this.line = line;
        this.count = count;
        this.start = start;
        this.end = end;
        this.supplementaryOffsets = supplementaryOffsets == null
                ? NO_SUPPLEMENTARY
                : supplementaryOffsets.clone();
    }

    /**
     * Converts a code point offset inside this line into the UTF-16 code unit offset expected by SonarQube.
     * A supplementary character standing before the requested position counts for 2 UTF-16 code units.
     *
     * @param codePointColumn code point offset, relative to the beginning of the line
     * @return the matching UTF-16 code unit offset
     */
    public int toUtf16Column(final int codePointColumn) {
        int extra = 0;
        for (final int offset : supplementaryOffsets) {
            if (offset >= codePointColumn) {
                break;
            }
            extra++;
        }
        return codePointColumn + extra;
    }

    @Override
    public String toString() {
        return "SourceLine [line=" + line + ", count=" + count + ", start=" + start + ", end=" + end
                + ", supplementaryOffsets=" + Arrays.toString(supplementaryOffsets) + "]";
    }

    public int getLine() {
        return line;
    }

    public int getCount() {
        return count;
    }

    public int getEnd() {
        return end;
    }

    public int getStart() {
        return start;
    }

}
