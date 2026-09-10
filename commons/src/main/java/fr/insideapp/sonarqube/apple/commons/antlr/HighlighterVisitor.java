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

import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.TextRange;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.cpd.NewCpdTokens;
import org.sonar.api.batch.sensor.highlighting.NewHighlighting;
import org.sonar.api.batch.sensor.highlighting.TypeOfText;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import java.util.Set;

import static java.lang.String.format;

public class HighlighterVisitor implements ParseTreeItemVisitor {
    private static final Logger LOGGER = Loggers.get(HighlighterVisitor.class);

    private final Set<Integer> commentTypes;
    private final Set<Integer> stringTypes;
    private final Set<Integer> preprocessTypes;
    private final Set<Integer> keywordLightTypes;
    private final Set<Integer> keywordTypes;
    private final int whitespaceType;

    public static class Builder {
        private Set<Integer> commentTypes = Set.of();
        private Set<Integer> stringTypes = Set.of();
        private Set<Integer> preprocessTypes = Set.of();
        private Set<Integer> keywordLightTypes = Set.of();
        private Set<Integer> keywordTypes = Set.of();
        private int whitespaceType = -1;

        public Builder commentTypes(Set<Integer> commentTypes) {
            this.commentTypes = commentTypes;
            return this;
        }

        public Builder stringTypes(Set<Integer> stringTypes) {
            this.stringTypes = stringTypes;
            return this;
        }

        public Builder preprocessTypes(Set<Integer> preprocessTypes) {
            this.preprocessTypes = preprocessTypes;
            return this;
        }

        public Builder keywordLightTypes(Set<Integer> keywordLightTypes) {
            this.keywordLightTypes = keywordLightTypes;
            return this;
        }

        public Builder keywordTypes(Set<Integer> keywordTypes) {
            this.keywordTypes = keywordTypes;
            return this;
        }

        public Builder whitespaceType(int whitespaceType) {
            this.whitespaceType = whitespaceType;
            return this;
        }

        public HighlighterVisitor build() { return new HighlighterVisitor(this); }
    }

    private HighlighterVisitor(Builder builder) {
        this.commentTypes = builder.commentTypes;
        this.stringTypes = builder.stringTypes;
        this.preprocessTypes = builder.preprocessTypes;
        this.keywordLightTypes = builder.keywordLightTypes;
        this.keywordTypes = builder.keywordTypes;
        this.whitespaceType = builder.whitespaceType;
    }

    @Override
    public void apply(ParseTree tree) {
        // no implementation needed
    }

    @Override
    public void fillContext(SensorContext context, AntlrContext antlrContext) {
        final InputFile file = antlrContext.getFile();
        if (file == null) {
            return;
        }
        final NewCpdTokens cpdTokens = context.newCpdTokens().onFile(file);
        final NewHighlighting newHighlighting = context.newHighlighting().onFile(file);

        for (final Token token : antlrContext.getTokens()) {
            if (token.getType() == Recognizer.EOF || token.getType() == whitespaceType) {
                continue;
            }

            // Index of the last character actually belonging to the token, trailing line terminators excluded:
            // some tokens swallow their end of line (Swift `Line_comment` is `'//' .*? ('\n' | EOF)`) and a
            // TextRange cannot span past the end of a line.
            final int lastIndex = lastSignificantIndex(token);
            // Single character tokens (mostly punctuation) are ignored
            if (lastIndex <= token.getStartIndex()) {
                continue;
            }

            // Both ends are resolved through the line table: `token.getLine()` / `token.getCharPositionInLine()`
            // are code point based, whereas SonarQube offsets are UTF-16 based.
            final int[] startDetails = antlrContext.getLineAndColumn(token.getStartIndex());
            final int[] endDetails = antlrContext.getLineAndColumn(lastIndex);

            if (startDetails == null || startDetails.length != 2 || endDetails == null || endDetails.length != 2) {
                continue;
            }

            final int startLine = startDetails[0];
            final int startLineOffset = startDetails[1];
            final int endLine = endDetails[0];
            // The line table gives the offset of the last character of the token, the end of a TextRange is
            // exclusive: hence the + 1.
            final int endLineOffset = endDetails[1] + 1;

            if (endLine < startLine || (endLine == startLine && endLineOffset <= startLineOffset)) {
                continue;
            }

            try {
                final TextRange range = file.newRange(startLine, startLineOffset, endLine, endLineOffset);
                addHighlighting(newHighlighting, token, file, range);
                addCpdToken(cpdTokens, file, token, range);
            } catch (final Exception e) {
                LOGGER.warn(format(
                                "Unexpected error creating text range on file %s for token %s on (%s, %s) -  (%s, %s)",
                                file.key(), token.getText(), startLine, startLineOffset, endLine, endLineOffset),
                        e);
            }
        }
        synchronized (HighlighterVisitor.class) {
            try {
                newHighlighting.save();
            } catch (Exception e) {
                LOGGER.warn(format("Unexpected error saving highlightings on file %s", file.key()), e);
            }

            try {
                cpdTokens.save();
            } catch (Exception e) {
                LOGGER.warn(format("Unexpected error saving cpd tokens on file %s", file.key()), e);
            }
        }
    }

    /**
     * Index of the last character of a token, trailing line terminators excluded.
     * Line terminators always are BMP characters, so removing them from a code point index is safe.
     */
    private static int lastSignificantIndex(final Token token) {
        final String text = token.getText();
        int index = token.getStopIndex();
        if (text == null) {
            return index;
        }
        for (int i = text.length() - 1; i >= 0 && index > token.getStartIndex(); i--) {
            final char character = text.charAt(i);
            if (character != '\n' && character != '\r') {
                break;
            }
            index--;
        }
        return index;
    }

    private void addCpdToken(NewCpdTokens cpdTokens, InputFile file, Token token, TextRange range) {
        try {
            cpdTokens.addToken(range, token.getText());
        } catch (Exception e) {
            LOGGER.debug(format("Unexpected error adding cpd tokens on file %s", file.key()), e);
        }
    }

    private void addHighlighting(NewHighlighting newHighlighting, Token token, InputFile file, TextRange range) {
        try {
            // Comment
            if (commentTypes.contains(token.getType())) {
                newHighlighting.highlight(range, TypeOfText.COMMENT);
                return;
            }

            // String
            if (stringTypes.contains(token.getType())) {
                newHighlighting.highlight(range, TypeOfText.STRING);
                return;
            }

            // Preprocessor
            if (preprocessTypes.contains(token.getType())) {
                newHighlighting.highlight(range, TypeOfText.PREPROCESS_DIRECTIVE);
                return;
            }

            // Constant
            if (keywordLightTypes.contains(token.getType())) {
                newHighlighting.highlight(range, TypeOfText.KEYWORD_LIGHT);
                return;
            }

            // Keyword
            if (keywordTypes.contains(token.getType())) {
                newHighlighting.highlight(range, TypeOfText.KEYWORD);
            }
        } catch (Exception e) {
            LOGGER.warn(format("Unexpected error adding highlighting on file %s", file.key()), e);
        }
    }
}
