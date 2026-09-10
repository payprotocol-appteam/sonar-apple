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
package fr.insideapp.sonarqube.swift.antlr;

import fr.insideapp.sonarqube.apple.commons.antlr.CustomTreeVisitor;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.highlighting.TypeOfText;
import org.sonar.api.batch.sensor.internal.SensorContextTester;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class SwiftHighlighterVisitorTest {

    private static final String TEST_ROOT = "src/test/resources/swift";
    private static final String TEST_FILENAME = "main.swift";
    private static final String HIGHLIGHTING_FILENAME = "highlighting.swift";

    private SensorContextTester highlight(String filename, Charset charset) throws IOException {
        SensorContextTester context = SensorContextTester.create(new File(TEST_ROOT));

        File file = new File(TEST_ROOT, filename);

        InputFile testFile = new TestInputFileBuilder("foo", filename)
                .setLanguage("swift")
                .setModuleBaseDir(Paths.get(TEST_ROOT))
                .setContents(FileUtils.readFileToString(file, charset))
                .setCharset(charset)
                .build();
        context.fileSystem().add(testFile);
        SwiftAntlrContext antlrContext = new SwiftAntlrContext();
        antlrContext.loadFromStreams(
                testFile,
                testFile.inputStream(),
                testFile.inputStream(),
                testFile.charset()
        );

        SwiftHighlighterVisitor highlighterVisitor = new SwiftHighlighterVisitor();
        CustomTreeVisitor customTreeVisitor = new CustomTreeVisitor(highlighterVisitor);
        customTreeVisitor.fillContext(context, antlrContext);
        return context;
    }

    @Test
    public void fillContext() throws IOException {
        SensorContextTester context = highlight(TEST_FILENAME, Charset.defaultCharset());
        String key = "foo:" + TEST_FILENAME;
        assertThat(context.highlightingTypeAt(key, 2, 0)).containsExactlyInAnyOrder(TypeOfText.KEYWORD);
        assertThat(context.highlightingTypeAt(key, 2, 10)).isEmpty();
        assertThat(context.highlightingTypeAt(key, 3, 8)).containsExactlyInAnyOrder(TypeOfText.KEYWORD);
        assertThat(context.highlightingTypeAt(key, 3, 13)).containsExactlyInAnyOrder(TypeOfText.KEYWORD);
        assertThat(context.highlightingTypeAt(key, 3, 19)).containsExactlyInAnyOrder(TypeOfText.KEYWORD_LIGHT);
        assertThat(context.highlightingTypeAt(key, 4, 20)).containsExactlyInAnyOrder(TypeOfText.STRING);
    }

    /**
     * Every token of `highlighting.swift` stands after an emoji (line 1). Before the code point / UTF-16 fix,
     * all of them were shifted by one, which silently dropped the two characters long ones
     * ("Unexpected error creating text range ... for token if") and mis-placed the others.
     */
    @Test
    public void fillContextWithSupplementaryCharacters() throws IOException {
        SensorContextTester context = highlight(HIGHLIGHTING_FILENAME, StandardCharsets.UTF_8);
        String key = "foo:" + HIGHLIGHTING_FILENAME;

        // 1 : /// 🧭 Documentation comment  -- a documentation comment, emoji included
        assertThat(context.highlightingTypeAt(key, 1, 0)).containsExactlyInAnyOrder(TypeOfText.COMMENT);
        // `Documentation` and `comment` stand after the emoji: their UTF-16 offsets are shifted by one
        assertThat(context.highlightingTypeAt(key, 1, 7)).containsExactlyInAnyOrder(TypeOfText.COMMENT);
        assertThat(context.highlightingTypeAt(key, 1, 27)).containsExactlyInAnyOrder(TypeOfText.COMMENT);

        // 2 : import Foundation
        assertThat(context.highlightingTypeAt(key, 2, 0)).containsExactlyInAnyOrder(TypeOfText.KEYWORD);
        assertThat(context.highlightingTypeAt(key, 2, 5)).containsExactlyInAnyOrder(TypeOfText.KEYWORD);
        assertThat(context.highlightingTypeAt(key, 2, 6)).isEmpty();
        assertThat(context.highlightingTypeAt(key, 2, 7)).containsExactlyInAnyOrder(TypeOfText.KEYWORD_LIGHT);

        // 4 : var counter = 0
        assertThat(context.highlightingTypeAt(key, 4, 0)).containsExactlyInAnyOrder(TypeOfText.KEYWORD);
        assertThat(context.highlightingTypeAt(key, 4, 2)).containsExactlyInAnyOrder(TypeOfText.KEYWORD);
        assertThat(context.highlightingTypeAt(key, 4, 4)).containsExactlyInAnyOrder(TypeOfText.KEYWORD_LIGHT);

        // 5 : let ratio = 0.1  -- a floating point literal is highlighted as a keyword
        assertThat(context.highlightingTypeAt(key, 5, 0)).containsExactlyInAnyOrder(TypeOfText.KEYWORD);
        assertThat(context.highlightingTypeAt(key, 5, 12)).containsExactlyInAnyOrder(TypeOfText.KEYWORD);
        assertThat(context.highlightingTypeAt(key, 5, 14)).containsExactlyInAnyOrder(TypeOfText.KEYWORD);

        // 6 : let name: String? = nil
        assertThat(context.highlightingTypeAt(key, 6, 10)).containsExactlyInAnyOrder(TypeOfText.KEYWORD_LIGHT);
        assertThat(context.highlightingTypeAt(key, 6, 20)).containsExactlyInAnyOrder(TypeOfText.KEYWORD);
        assertThat(context.highlightingTypeAt(key, 6, 22)).containsExactlyInAnyOrder(TypeOfText.KEYWORD);

        // 9 :     for item in items {
        assertThat(context.highlightingTypeAt(key, 9, 3)).isEmpty();
        assertThat(context.highlightingTypeAt(key, 9, 4)).containsExactlyInAnyOrder(TypeOfText.KEYWORD);
        assertThat(context.highlightingTypeAt(key, 9, 6)).containsExactlyInAnyOrder(TypeOfText.KEYWORD);
        assertThat(context.highlightingTypeAt(key, 9, 8)).containsExactlyInAnyOrder(TypeOfText.KEYWORD_LIGHT);
        assertThat(context.highlightingTypeAt(key, 9, 13)).containsExactlyInAnyOrder(TypeOfText.KEYWORD);
        assertThat(context.highlightingTypeAt(key, 9, 14)).containsExactlyInAnyOrder(TypeOfText.KEYWORD);
        assertThat(context.highlightingTypeAt(key, 9, 16)).containsExactlyInAnyOrder(TypeOfText.KEYWORD_LIGHT);

        // 10 :         if item > 0 {  -- the exact shape of the reported warning
        assertThat(context.highlightingTypeAt(key, 10, 7)).isEmpty();
        assertThat(context.highlightingTypeAt(key, 10, 8)).containsExactlyInAnyOrder(TypeOfText.KEYWORD);
        assertThat(context.highlightingTypeAt(key, 10, 9)).containsExactlyInAnyOrder(TypeOfText.KEYWORD);
        assertThat(context.highlightingTypeAt(key, 10, 10)).isEmpty();
        assertThat(context.highlightingTypeAt(key, 10, 11)).containsExactlyInAnyOrder(TypeOfText.KEYWORD_LIGHT);
    }
}
