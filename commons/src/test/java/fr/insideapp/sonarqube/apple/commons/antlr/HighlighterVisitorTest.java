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
import fr.insideapp.sonarqube.apple.commons.SourceLinesProvider;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;
import org.junit.Test;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.highlighting.TypeOfText;
import org.sonar.api.batch.sensor.internal.SensorContextTester;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Set;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class HighlighterVisitorTest {
    private static final int COMMENT_TYPE = 2;
    private static final int STRING_TYPE = 4;
    private static final int PREPROCESS_TYPE = 6;
    private static final int KEYWORD_LIGHT_TYPE = 8;
    private static final int KEYWORD_TYPE = 10;
    private static final int WHITESPACE_TYPE = 100;

    private static final Set<Integer> commentTypes = Set.of(1, 2, 3);
    private static final Set<Integer> stringTypes = Set.of(4, 5);
    private static final Set<Integer> preprocessTypes = Set.of(6, 7);
    private static final Set<Integer> keywordLightTypes = Set.of(8);
    private static final Set<Integer> keywordTypes = Set.of(9, 10, 11);

    /**
     * Minimal {@link AntlrContext} holding a line table and a list of tokens, so that the real
     * {@link AntlrContext#getLineAndColumn(int)} implementation is exercised.
     */
    private static final class StubAntlrContext extends AntlrContext {

        private Token[] tokens = new Token[0];

        @Override
        public void loadFromStreams(InputFile inputFile, InputStream file, InputStream linesStream, Charset charset) {
            setFile(inputFile);
            setLines(new SourceLinesProvider().getLines(linesStream, charset));
        }

        void load(InputFile inputFile, String contents, Token... tokens) {
            loadFromStreams(inputFile, null,
                    new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
            this.tokens = tokens;
        }

        @Override
        public Token[] getTokens() {
            return tokens;
        }
    }

    private static Token token(int type, String text, int startIndex) {
        final CommonToken token = new CommonToken(type, text);
        token.setStartIndex(startIndex);
        // A token index is a code point index: a supplementary character counts for one
        token.setStopIndex(startIndex + text.codePointCount(0, text.length()) - 1);
        return token;
    }

    private static HighlighterVisitor visitor() {
        return new HighlighterVisitor.Builder()
                .commentTypes(commentTypes)
                .stringTypes(stringTypes)
                .preprocessTypes(preprocessTypes)
                .keywordLightTypes(keywordLightTypes)
                .keywordTypes(keywordTypes)
                .whitespaceType(WHITESPACE_TYPE)
                .build();
    }

    private static DefaultInputFile inputFile(String contents) {
        return new TestInputFileBuilder("foo", "test.extension")
                .setModuleBaseDir(Paths.get("/"))
                .setCharset(StandardCharsets.UTF_8)
                .setContents(contents)
                .initMetadata(contents)
                .build();
    }

    @Test
    public void fillContext() {
        // 1 : let ab = 1
        // 2 : // hi
        // 3 : "text"
        // 4 : #import
        final String contents = "let ab = 1\n// hi\n\"text\"\n#import\n";
        SensorContextTester sensorContext = SensorContextTester.create(new File(""));
        DefaultInputFile testFile = inputFile(contents);
        sensorContext.fileSystem().add(testFile);

        StubAntlrContext antlrContext = new StubAntlrContext();
        antlrContext.load(testFile, contents,
                token(KEYWORD_TYPE, "let", 0),
                token(KEYWORD_LIGHT_TYPE, "ab", 4),
                token(COMMENT_TYPE, "// hi\n", 11),
                token(STRING_TYPE, "\"text\"", 17),
                token(PREPROCESS_TYPE, "#import", 24));

        visitor().fillContext(sensorContext, antlrContext);

        assertThat(sensorContext.highlightingTypeAt(testFile.key(), 1, 0))
                .containsExactlyInAnyOrder(TypeOfText.KEYWORD);
        assertThat(sensorContext.highlightingTypeAt(testFile.key(), 1, 2))
                .containsExactlyInAnyOrder(TypeOfText.KEYWORD);
        assertThat(sensorContext.highlightingTypeAt(testFile.key(), 1, 3)).isEmpty();
        assertThat(sensorContext.highlightingTypeAt(testFile.key(), 1, 4))
                .containsExactlyInAnyOrder(TypeOfText.KEYWORD_LIGHT);
        assertThat(sensorContext.highlightingTypeAt(testFile.key(), 1, 5))
                .containsExactlyInAnyOrder(TypeOfText.KEYWORD_LIGHT);
        assertThat(sensorContext.highlightingTypeAt(testFile.key(), 1, 6)).isEmpty();
        // The line comment token swallows its end of line: the range must stop at the end of the line
        assertThat(sensorContext.highlightingTypeAt(testFile.key(), 2, 0))
                .containsExactlyInAnyOrder(TypeOfText.COMMENT);
        assertThat(sensorContext.highlightingTypeAt(testFile.key(), 2, 4))
                .containsExactlyInAnyOrder(TypeOfText.COMMENT);
        assertThat(sensorContext.highlightingTypeAt(testFile.key(), 3, 5))
                .containsExactlyInAnyOrder(TypeOfText.STRING);
        assertThat(sensorContext.highlightingTypeAt(testFile.key(), 4, 6))
                .containsExactlyInAnyOrder(TypeOfText.PREPROCESS_DIRECTIVE);
    }

    /**
     * Regression test for the "Unexpected error creating text range" warnings: ANTLR indexes count code points
     * whereas SonarQube offsets count UTF-16 code units. Before the fix, every token following a supplementary
     * character (an emoji, typically) was shifted by one, producing empty or reversed ranges.
     */
    @Test
    public void fillContextWithSupplementaryCharacters() {
        // 1 : // 🧭 nav
        // 2 :     if ok {}
        final String contents = "// 🧭 nav\n    if ok {}\n";
        SensorContextTester sensorContext = SensorContextTester.create(new File(""));
        DefaultInputFile testFile = inputFile(contents);
        sensorContext.fileSystem().add(testFile);

        // Code point indexes: the comment spans 0 to 8, line 2 starts at 9, `if` at 13 and `ok` at 16
        StubAntlrContext antlrContext = new StubAntlrContext();
        antlrContext.load(testFile, contents,
                token(COMMENT_TYPE, "// 🧭 nav\n", 0),
                token(KEYWORD_TYPE, "if", 13),
                token(KEYWORD_LIGHT_TYPE, "ok", 16));

        visitor().fillContext(sensorContext, antlrContext);

        // `nav` sits after the emoji: its UTF-16 offsets are shifted by one compared to the code point ones
        assertThat(sensorContext.highlightingTypeAt(testFile.key(), 1, 6))
                .containsExactlyInAnyOrder(TypeOfText.COMMENT);
        assertThat(sensorContext.highlightingTypeAt(testFile.key(), 1, 8))
                .containsExactlyInAnyOrder(TypeOfText.COMMENT);
        assertThat(sensorContext.highlightingTypeAt(testFile.key(), 2, 4))
                .containsExactlyInAnyOrder(TypeOfText.KEYWORD);
        assertThat(sensorContext.highlightingTypeAt(testFile.key(), 2, 5))
                .containsExactlyInAnyOrder(TypeOfText.KEYWORD);
        assertThat(sensorContext.highlightingTypeAt(testFile.key(), 2, 6)).isEmpty();
        assertThat(sensorContext.highlightingTypeAt(testFile.key(), 2, 7))
                .containsExactlyInAnyOrder(TypeOfText.KEYWORD_LIGHT);
    }

    @Test
    public void lineAndColumnAreUtf16Based() {
        final SourceLine[] lines = new SourceLinesProvider().getLines(
                new ByteArrayInputStream("a🧭b\nc\n".getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
        StubAntlrContext antlrContext = new StubAntlrContext();
        antlrContext.setLines(lines);

        // 4 code points on the first line: a, the emoji, b and the end of line
        assertThat(lines[0].getStart()).isZero();
        assertThat(lines[0].getEnd()).isEqualTo(4);
        assertThat(antlrContext.getLineAndColumn(0)).containsExactly(1, 0);
        assertThat(antlrContext.getLineAndColumn(1)).containsExactly(1, 1);
        // `b` is the third code point but the fourth UTF-16 code unit
        assertThat(antlrContext.getLineAndColumn(2)).containsExactly(1, 3);
        assertThat(antlrContext.getLineAndColumn(4)).containsExactly(2, 0);
        assertThat(antlrContext.getLineAndColumn(1000)).isEmpty();
    }
}
