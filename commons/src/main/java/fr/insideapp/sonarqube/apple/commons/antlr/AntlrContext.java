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
package fr.insideapp.sonarqube.apple.commons.antlr;

import fr.insideapp.sonarqube.apple.commons.SourceLine;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.sonar.api.batch.fs.InputFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

public abstract class AntlrContext {

    private InputFile file;
    private CommonTokenStream stream;
    private ParseTree root;
    private SourceLine[] lines;

    public void loadFromFile(InputFile file, Charset charset) throws IOException {
        loadFromStreams(file, file.inputStream(), file.inputStream(), charset);
    }

    public abstract void loadFromStreams(InputFile inputFile, InputStream file, InputStream linesStream,
                                         Charset charset) throws IOException;

    public SourceLine[] getLines() {
        return lines;
    }

    public Token[] getTokens() {
        return this.stream.getTokens().toArray(new Token[0]);
    }

    /**
     * Locates a position of the file.
     *
     * @param global index of the character, in <b>code points</b> (the unit used by the ANTLR
     *               {@code CodePointCharStream} the tokens come from)
     * @return a two elements array holding the 1-based line number and the 0-based <b>UTF-16</b> offset of the
     *         character inside that line (the unit SonarQube {@code TextRange} offsets are expressed in), or an
     *         empty array when the index is out of the file bounds
     */
    public int[] getLineAndColumn(final int global) {
        if (this.lines == null) {
            return new int[0];
        }
        // Lines are contiguous and sorted: binary search instead of walking the whole file for every token
        int low = 0;
        int high = this.lines.length - 1;
        while (low <= high) {
            final int mid = (low + high) >>> 1;
            final SourceLine line = this.lines[mid];
            if (line.getEnd() <= global) {
                low = mid + 1;
            } else if (line.getStart() > global) {
                high = mid - 1;
            } else {
                return new int[]{line.getLine(), line.toUtf16Column(global - line.getStart())};
            }
        }
        return new int[0];
    }

    public InputFile getFile() {
        return file;
    }

    protected void setFile(InputFile file) {
        this.file = file;
    }

    public CommonTokenStream getStream() {
        return stream;
    }

    public ParseTree getRoot() {
        return root;
    }

    protected void setStream(CommonTokenStream stream) {
        this.stream = stream;
    }

    protected void setRoot(ParseTree root) {
        this.root = root;
    }

    protected void setLines(SourceLine[] lines) {
        this.lines = lines;
    }
}
