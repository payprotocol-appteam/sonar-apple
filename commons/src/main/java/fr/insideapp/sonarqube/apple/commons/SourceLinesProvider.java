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

import org.apache.commons.io.input.BOMInputStream;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class SourceLinesProvider {
    private static final Logger LOGGER = Loggers.get(SourceLinesProvider.class);

    /**
     * Builds the line table of a file.
     * <p>
     * Positions are counted in <b>Unicode code points</b>, to stay aligned with the token indexes reported by ANTLR
     * ({@code CodePointCharStream}). A surrogate pair therefore counts for a single position, and the offset of every
     * supplementary character is recorded so that the column can later be translated back to the UTF-16 offset
     * SonarQube expects (see {@link SourceLine#toUtf16Column(int)}).
     */
    public SourceLine[] getLines(final InputStream inputStream, final Charset charset) {
        if (inputStream == null) {
            return new SourceLine[0];
        }
        final List<SourceLine> sourceLines = new ArrayList<>();

        try (final BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(bomInputStream(inputStream), charset))) {
            int totalLines = 1;
            int global = 0;
            int count = 0;
            final List<Integer> supplementaryOffsets = new ArrayList<>();

            int currentChar;
            while ((currentChar = bufferedReader.read()) != -1) {
                if (Character.isHighSurrogate((char) currentChar)) {
                    bufferedReader.mark(1);
                    final int lowSurrogate = bufferedReader.read();
                    if (lowSurrogate != -1 && Character.isLowSurrogate((char) lowSurrogate)) {
                        // Single code point, but two UTF-16 code units: consume both and remember the offset
                        supplementaryOffsets.add(count);
                    } else if (lowSurrogate != -1) {
                        bufferedReader.reset();
                    }
                }
                global++;
                count++;
                if (currentChar == 10) {
                    sourceLines.add(newSourceLine(totalLines, count, global, supplementaryOffsets));
                    totalLines++;
                    count = 0;
                    supplementaryOffsets.clear();
                }

            }
            sourceLines.add(newSourceLine(totalLines, count, global, supplementaryOffsets));
        } catch (final Exception e) {
            LOGGER.warn("Error occurred reading file", e);
        }

        return sourceLines.toArray(new SourceLine[0]);
    }

    private static SourceLine newSourceLine(final int line, final int count, final int global,
                                            final List<Integer> supplementaryOffsets) {
        return new SourceLine(line, count, global - count, global,
                supplementaryOffsets.stream().mapToInt(Integer::intValue).toArray());
    }

    public BOMInputStream bomInputStream(final InputStream inputStream) throws IOException {
        return BOMInputStream.builder()
            .setInputStream(inputStream)
            .setInclude(false)
            .get();
    }

}
