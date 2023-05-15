/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2023 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.quest.editor;

import io.quest.GTk;
import io.questdb.cairo.ColumnType;
import io.questdb.griffin.FunctionFactory;
import io.questdb.griffin.engine.functions.catalogue.Constants;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class EditorHighlighter extends DocumentFilter {

    public static final String EVENT_TYPE = "style change";
    public static final int PATTERN_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    protected static final AttributeSet HIGHLIGHT_STATIC = styleForegroundColor(
        Color.WHITE.getRed(),
        Color.WHITE.getGreen(),
        Color.WHITE.getBlue());
    protected static final AttributeSet HIGHLIGHT_COMMENT = styleForegroundColor(
        GTk.EDITOR_LINENO_COLOR.getRed(),
        GTk.EDITOR_LINENO_COLOR.getGreen(),
        GTk.EDITOR_LINENO_COLOR.getBlue());
    protected static final AttributeSet HIGHLIGHT_FUNCTION = styleForegroundColor(
        GTk.EDITOR_FUNCTION_FOREGROUND_COLOR.darker().getRed(),
        GTk.EDITOR_FUNCTION_FOREGROUND_COLOR.darker().getGreen(),
        GTk.EDITOR_FUNCTION_FOREGROUND_COLOR.darker().getBlue());
    protected static final AttributeSet HIGHLIGHT_KEYWORD = styleForegroundColor(
        GTk.EDITOR_KEYWORD_FOREGROUND_COLOR.getRed(),
        GTk.EDITOR_KEYWORD_FOREGROUND_COLOR.getGreen(),
        GTk.EDITOR_KEYWORD_FOREGROUND_COLOR.getBlue());
    protected static final AttributeSet HIGHLIGHT_TYPE = styleForegroundColor(
        GTk.EDITOR_TYPE_FOREGROUND_COLOR.getRed(),
        GTk.EDITOR_TYPE_FOREGROUND_COLOR.getGreen(),
        GTk.EDITOR_TYPE_FOREGROUND_COLOR.getBlue());
    protected static final AttributeSet HIGHLIGHT_NORMAL = styleForegroundColor(
        GTk.EDITOR_NORMAL_FOREGROUND_COLOR.getRed(),
        GTk.EDITOR_NORMAL_FOREGROUND_COLOR.getGreen(),
        GTk.EDITOR_NORMAL_FOREGROUND_COLOR.getBlue());
    private static final AttributeSet HIGHLIGHT_FIND_MATCH = styleForegroundColor(
        GTk.EDITOR_MATCH_FOREGROUND_COLOR.getRed(),
        GTk.EDITOR_MATCH_FOREGROUND_COLOR.getGreen(),
        GTk.EDITOR_MATCH_FOREGROUND_COLOR.getBlue());
    private static final AttributeSet HIGHLIGHT_ERROR = styleForegroundColor(
        GTk.EDITOR_ERROR_FOREGROUND_COLOR.getRed(),
        GTk.EDITOR_ERROR_FOREGROUND_COLOR.getGreen(),
        GTk.EDITOR_ERROR_FOREGROUND_COLOR.getBlue());
    private static final Pattern COMMENT_PATTERN = Pattern.compile("--[^\n]*\n?", PATTERN_FLAGS);
    private static final Pattern STATIC_PATTERN = Pattern.compile("\\+|\\-|\\*|/|%|:|;|&|\\||~|!|\\^|=|>|<|\\.|,|\\\\|\\(|\\)|\\[|\\]|\\{|\\}|'|\"", PATTERN_FLAGS);
    private static final Pattern KEYWORDS_PATTERN;
    private static final Pattern TYPES_PATTERN;
    private static final Pattern FUNCTION_NAMES_PATTERN;
    private static final String ERROR_HEADER = "==========  ERROR  ==========\n";
    private static final Pattern ERROR_HEADER_PATTERN = Pattern.compile(ERROR_HEADER);

    static {
        // static
        final Set<String> staticSet = Set.of(
            "&", "|", "^", "~", "[]",
            "!=", "!~", "%", "*", "+",
            "-", ".", "/", "<", "<=",
            "<>", "<>all", "=", ">", ">=");

        // function names
        final Set<String> names = new TreeSet<>();
        for (FunctionFactory factory : ServiceLoader.load(FunctionFactory.class, FunctionFactory.class.getClassLoader())) {
            if (factory.getClass().getName().contains("test")) {
                continue;
            }
            String signature = factory.getSignature();
            String name = signature.substring(0, signature.indexOf('('));
            if (staticSet.contains(name)) {
                continue;
            }
            names.add(name);
            // add != counterparts to equality function factories
            if (factory.isBoolean()) {
                switch (name) {
                    case "=" -> {
                        names.add("!=");
                        names.add("<>");
                    }
                    case "<" -> {
                        names.add("<=");
                        names.add(">=");
                        names.add(">");
                    }
                }
            }
        }
        FUNCTION_NAMES_PATTERN = Pattern.compile(preCompileKeywords(names, true), PATTERN_FLAGS);

        // keywords
        names.clear();
        try {
            Field field = Constants.class.getDeclaredField("KEYWORDS");
            field.setAccessible(true);
            for (CharSequence keyword : (CharSequence[]) field.get(null)) {
                names.add((String) keyword);
            }
            names.add("size");
            names.add("txn");
            names.add("cv");
            KEYWORDS_PATTERN = Pattern.compile(preCompileKeywords(names, false), PATTERN_FLAGS);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        // types
        names.clear();
        final Set<String> skipSet = Set.of("unknown", "regclass", "regprocedure", "VARARG", "text[]", "CURSOR", "RECORD", "PARAMETER");
        for (int type = 1; type < ColumnType.MAX; type++) {
            String name = ColumnType.nameOf(type);
            if (!skipSet.contains(name)) {
                names.add(name.toLowerCase());
            }
        }
        TYPES_PATTERN = Pattern.compile(preCompileKeywords(names, false), PATTERN_FLAGS);
    }

    protected final StyledDocument styledDocument;
    private final StringBuilder errorBuilder;
    private final int errorHeaderLen;
    private final WeakHashMap<String, Pattern> findPatternCache;

    protected EditorHighlighter(StyledDocument styledDocument) {
        this.styledDocument = Objects.requireNonNull(styledDocument);
        findPatternCache = new WeakHashMap<>(5, 0.2f); // one at the time
        errorBuilder = new StringBuilder();
        errorBuilder.append("\n").append(ERROR_HEADER).append("\n");
        errorHeaderLen = errorBuilder.length();
    }

    public static EditorHighlighter of(JTextPane textPane) {
        EditorHighlighter highlighter = new EditorHighlighter(textPane.getStyledDocument()); // produces EVENT_TYPE
        AbstractDocument doc = (AbstractDocument) textPane.getDocument();
        doc.putProperty(DefaultEditorKit.EndOfLineStringProperty, "\n");
        doc.setDocumentFilter(highlighter);
        return highlighter;
    }

    private static String replaceAllTabs(String text) {
        return text.replaceAll("\t", "    ");
    }

    protected static AttributeSet styleForegroundColor(int r, int g, int b) {
        StyleContext sc = StyleContext.getDefaultStyleContext();
        return sc.addAttribute(sc.getEmptySet(), StyleConstants.Foreground, new Color(r, g, b));
    }

    protected static String preCompileKeywords(Set<String> keywords) {
        return preCompileKeywords(keywords, false);

    }

    protected static String preCompileKeywords(Set<String> keywords, boolean isFunction) {
        if (keywords.isEmpty()) {
            throw new IllegalArgumentException("keywords cannot be empty");
        }
        // | Boundary Construct | Description             |
        // | ================== | ======================= |
        // |       ^            | The beginning of a line |
        // |       $            | The end of a line       |
        // |       \\b          | A word boundary         |
        // |       \\B          | A non-word boundary     |
        // https://docs.oracle.com/javase/tutorial/essential/regex/bounds.html
        StringBuilder sb = new StringBuilder();
        for (String key : keywords) {
            sb.append("\\b").append(Pattern.quote(key)).append(isFunction ? "\\(" : "\\b").append('|');
        }
        sb.setLength(sb.length() - 1); // remove last "|"
        return sb.toString();
    }

    @Override
    public void insertString(FilterBypass fb, int offset, String text, AttributeSet attributeSet) {
        try {
            super.insertString(fb, offset, replaceAllTabs(text), attributeSet);
            handleTextChanged();
        } catch (BadLocationException irrelevant) {
            // do nothing
        }
    }

    @Override
    public void remove(FilterBypass fb, int offset, int length) {
        try {
            super.remove(fb, offset, length);
            handleTextChanged();
        } catch (BadLocationException irrelevant) {
            // do nothing
        }
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrSet) {
        try {
            super.replace(fb, offset, length, replaceAllTabs(text), attrSet);
            handleTextChanged();
        } catch (BadLocationException irrelevant) {
            // do nothing
        }
    }

    public String highlightError(Throwable error) {
        errorBuilder.setLength(errorHeaderLen);
        try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
            error.printStackTrace(pw);
            errorBuilder.append(sw);
            return errorBuilder.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String highlightError(String error) {
        errorBuilder.setLength(errorHeaderLen);
        errorBuilder.append(error);
        return errorBuilder.toString();
    }

    public int handleTextChanged() {
        return handleTextChanged(null, null);
    }

    public int handleTextChanged(String findRegex, String replaceWith) {
        int len = styledDocument.getLength();
        if (len > 0) {
            String txt;
            try {
                txt = styledDocument.getText(0, len);
            } catch (BadLocationException impossible) {
                return 0;
            }
            if (ERROR_HEADER_PATTERN.matcher(txt).find()) {
                styledDocument.setCharacterAttributes(0, len, HIGHLIGHT_ERROR, true);
            } else {
                styledDocument.setCharacterAttributes(0, len, HIGHLIGHT_NORMAL, true);
                handleTextChanged(txt);
                return applyFindReplace(findRegex, replaceWith, txt);
            }
        }
        return 0;
    }

    protected void handleTextChanged(String txt) {
        applyStyle(FUNCTION_NAMES_PATTERN.matcher(txt), HIGHLIGHT_FUNCTION);
        applyStyleReplacing(STATIC_PATTERN.matcher(txt), HIGHLIGHT_STATIC);
        applyStyleReplacing(KEYWORDS_PATTERN.matcher(txt), HIGHLIGHT_KEYWORD);
        applyStyleReplacing(TYPES_PATTERN.matcher(txt), HIGHLIGHT_TYPE);
        applyStyleReplacing(COMMENT_PATTERN.matcher(txt), HIGHLIGHT_COMMENT);
    }

    protected void applyStyle(Matcher matcher, AttributeSet style) {
        applyStyle(matcher, style, false);
    }

    protected int applyStyleReplacing(Matcher matcher, AttributeSet style) {
        return applyStyle(matcher, style, true);
    }

    private int applyStyle(Matcher matcher, AttributeSet style, boolean replace) {
        int matchCount = 0;
        while (matcher.find()) {
            styledDocument.setCharacterAttributes(
                matcher.start(),
                matcher.end() - matcher.start(),
                style,
                replace);
            matchCount++;
        }
        return matchCount;
    }

    private int applyFindReplace(String findRegex, String replaceWith, String txt) {
        if (findRegex != null && !findRegex.isBlank()) {
            Pattern find = findPatternCache.get(findRegex);
            if (find == null) {
                try {
                    find = Pattern.compile(findRegex, PATTERN_FLAGS);
                    findPatternCache.put(findRegex, find);
                } catch (PatternSyntaxException err) {
                    JOptionPane.showMessageDialog(
                        null,
                        String.format("Not a valid regex: %s", findRegex)
                    );
                    return 0;
                }
            }
            return replaceWith == null ?
                applyStyleReplacing(find.matcher(txt), HIGHLIGHT_FIND_MATCH)
                :
                replaceAllWith(find.matcher(txt), replaceWith);
        }
        return 0;
    }

    private int replaceAllWith(Matcher matcher, String replaceWith) {
        int matchCount = 0;
        while (matcher.find()) {
            matchCount++;
        }
        matcher.reset();
        String ignore = matcher.replaceAll(replaceWith);
        return matchCount;
    }
}
