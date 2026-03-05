package net.jaraonthe.java.lsp_display_examples;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tokenizer used by the language server. Provides LSP semantic tokens and
 * diagnostics.
 *
 * @author Jakob Rathbauer <jakob@jaraonthe.net>
 */
public final class ExamplesTokenizer
{
    private static final Logger log = LoggerFactory.getLogger(ExamplesTokenizer.class);

    public static final char[] MODIFIER_SEPARATORS = {':', '.', '_'};
    public static final char ALL_MODIFIERS_OPERATOR = '|';
    public static final List<String> ALL_TOKEN_TYPES;
    public static final List<String> ALL_TOKEN_MODIFIERS;

    private static final Map<String, String> ALL_TOKEN_TYPES_LOWERCASE;
    private static final Map<String, String> ALL_TOKEN_MODIFIERS_LOWERCASE;
    private static final List<Set<String>> ALL_MODIFIER_COMBINATIONS;

    /**
     * Maps token type string (as used in {@code ALL_TOKEN_TYPES}) to their
     * numeric representation (aka their index in the capabilities array).
     */
    private final Map<String, Integer> tokenTypesMap;
    /**
     * Maps token modifier string (as used in {@code ALL_TOKEN_MODIFIERS}) to
     * their bit flag representation (derived from their index in the
     * capabilities array).
     */
    private final Map<String, Integer> tokenModifiersMap;


    static {
        ALL_TOKEN_TYPES = allStringConstantValues(SemanticTokenTypes.class);
        ALL_TOKEN_MODIFIERS = allStringConstantValues(SemanticTokenModifiers.class);

        ALL_TOKEN_TYPES_LOWERCASE = lowercaseMap(ALL_TOKEN_TYPES);
        ALL_TOKEN_MODIFIERS_LOWERCASE = lowercaseMap(ALL_TOKEN_MODIFIERS);

        List<Set<String>> allModifierCombinations = new ArrayList<>(1 << ALL_TOKEN_MODIFIERS.size());
        allModifierCombinations.add(Set.of());
        for (String modifier : ALL_TOKEN_MODIFIERS) {

            // Add new modifier to copies of all existing sets
            List<Set<String>> newCombinations = new ArrayList<>(allModifierCombinations.size());
            for (Set<String> existingSet : allModifierCombinations) {
                Set<String> newSet = new HashSet<>(existingSet);
                newSet.add(modifier);
                newCombinations.add(Collections.unmodifiableSet(newSet));
            }
            allModifierCombinations.addAll(newCombinations);
        }
        ALL_MODIFIER_COMBINATIONS = Collections.unmodifiableList(allModifierCombinations);
    }

    private static List<String> allStringConstantValues(Class<?> clazz)
    {
        List<String> constantValues = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            var m = field.getModifiers();
            if (
                !Modifier.isPublic(m) || !Modifier.isStatic(m) || !Modifier.isFinal(m)
                || !String.class.isAssignableFrom(field.getType())
            ) {
                continue;
            }
            try {
                constantValues.add((String)field.get(null));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return Collections.unmodifiableList(constantValues);
    }

    private static Map<String, String> lowercaseMap(List<String> strings)
    {
        Map<String, String> map = new HashMap<>();
        for (String string : strings) {
            map.put(string.toLowerCase(), string);
        }
        return Collections.unmodifiableMap(map);
    }


    /**
     * Provides the file content for an example file which showcases every
     * semantic token type but no modifiers.
     */
    public static String getTypesExample()
    {
        return String.join("\n", ALL_TOKEN_TYPES) + "\n";
    }

    /**
     * Provides the file content for an example file which showcases every
     * semantic token type combined with each of the modifiers (but no more than
     * one modifier at a time).
     */
    public static String getExampleWithSingleModifiers()
    {
        int maxTypeLength = ALL_TOKEN_TYPES.stream().map(String::length)
            .reduce(Math::max).orElse(0);

        return ALL_TOKEN_TYPES.stream().map((t) -> {
            String padding = " ".repeat(maxTypeLength - t.length() + 2);
            return t + ":" + padding + String.join("  ", ALL_TOKEN_MODIFIERS);
        }).collect(Collectors.joining("\n")) + "\n";
    }

    /**
     * Provides the file content for an example file which showcases every
     * semantic token type combined with all possible modifier combinations
     * (utilizing the | operator).
     */
    public static String getExampleWithAllModifierCombinations()
    {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 1 << ALL_TOKEN_MODIFIERS.size(); i++) {
            // Append a-z continuously
            builder.append((char) ('a' + (i % ('z' - 'a' + 1))));
        }
        String placeholders = builder.toString();
        int maxTypeLength = ALL_TOKEN_TYPES.stream().map(String::length)
            .reduce(Math::max).orElse(0);

        return ALL_TOKEN_TYPES.stream().map((t) ->
            " ".repeat(maxTypeLength - t.length()) + t + ALL_MODIFIERS_OPERATOR + placeholders
        ).collect(Collectors.joining("\n")) + "\n";
    }


    /**
     * Creates a new tokenizer.
     *
     * @param serverCapabilities The capabilities this server has
     */
    public ExamplesTokenizer(ServerCapabilities serverCapabilities)
    {
        tokenTypesMap = new HashMap<>();
        int index = 0;
        List<String> unknownTokenTypes = new ArrayList<>();
        for (String type : serverCapabilities.getSemanticTokensProvider().getLegend().getTokenTypes()) {
            tokenTypesMap.put(type, index);
            index++;
            if (!ALL_TOKEN_TYPES.contains(type)) {
                unknownTokenTypes.add(type);
            }
        }
        tokenModifiersMap = new HashMap<>();
        int bitFlag = 1;
        List<String> unknownTokenModifiers = new ArrayList<>();
        for (String modifier : serverCapabilities.getSemanticTokensProvider().getLegend().getTokenModifiers()) {
            tokenModifiersMap.put(modifier, bitFlag);
            bitFlag <<= 1;
            if (!ALL_TOKEN_MODIFIERS.contains(modifier)) {
                unknownTokenModifiers.add(modifier);
            }
        }

        if (tokenTypesMap.size() < ALL_TOKEN_TYPES.size()) {
            log.warn(
                "Client does not support the following semantic token types: {}",
                ALL_TOKEN_TYPES.stream()
                    .filter((s) -> !tokenTypesMap.containsKey(s))
                    .toList()
            );
        }
        if (!unknownTokenTypes.isEmpty()) {
            log.warn(
                "Client supports the following additional semantic token types: {}",
                unknownTokenTypes
            );
        }

        if (tokenModifiersMap.size() < ALL_TOKEN_MODIFIERS.size()) {
            log.warn(
                "Client does not support the following semantic token modifiers: {}",
                ALL_TOKEN_MODIFIERS.stream()
                    .filter((s) -> !tokenModifiersMap.containsKey(s))
                    .toList()
            );
        }
        if (!unknownTokenModifiers.isEmpty()) {
            log.warn(
                "Client supports the following additional semantic token modifiers: {}",
                unknownTokenModifiers
            );
        }
    }

    /**
     * Returns LSP Tokens and diagnostics for the given source code.
     *
     * @param textLines all lines of a file
     */
    public Result getTokens(List<String> textLines)
    {
        List<Integer> tokens = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        int previousLine = 0;
        int previousStart = 0;
        Scanner s = new Scanner(textLines);
        List<String> unsupportedModifiers = new ArrayList<>(ALL_TOKEN_MODIFIERS.size());

        while (true) {
            try {
                if (!s.next()) {
                    break;
                }
            } catch (PrematureLineEndingException e) {
                var d = new Diagnostic(
                    new Range(new Position(e.line, e.col), new Position(e.line, e.col)),
                    "Line is too short to showcase all modifier combinations.\nPlease add more text."
                );
                d.setSeverity(DiagnosticSeverity.Error);
                diagnostics.add(d);
                continue;
            }

            Integer typeNumber = tokenTypesMap.get(s.tokenType);

            int modifiers = 0;
            unsupportedModifiers.clear();
            for (String tokenModifier : s.tokenModifiers) {
                Integer modifierFlag = tokenModifiersMap.get(tokenModifier);
                if (modifierFlag == null) {
                    unsupportedModifiers.add(tokenModifier);
                    continue;
                }
                modifiers += modifierFlag;
            }

            if (typeNumber == null || !unsupportedModifiers.isEmpty()) {
                String message = "Client does not support ";
                if (typeNumber == null) {
                    message += "type " + s.tokenType;
                    if (!unsupportedModifiers.isEmpty()) {
                        message += "and ";
                    }
                }
                if (!unsupportedModifiers.isEmpty()) {
                    message += "modifiers " + String.join(", ", unsupportedModifiers);
                }

                var d = new Diagnostic(
                    new Range(new Position(s.line, s.startCol), new Position(s.line, s.endCol)),
                    message
                );
                d.setSeverity(DiagnosticSeverity.Warning);
                diagnostics.add(d);

                if (typeNumber == null) {
                    continue;
                }
            }

            int deltaLine = s.line - previousLine;
            previousLine = s.line;
            if (deltaLine != 0) {
                previousStart = 0;
            }
            int deltaStart = s.startCol - previousStart;
            previousStart = s.startCol;

            // deltaLine, deltaStart, length, tokenType, tokenModifiers
            tokens.add(deltaLine);
            tokens.add(deltaStart);
            tokens.add(s.endCol - s.startCol);
            tokens.add(typeNumber);
            tokens.add(modifiers);
        }

        return new Result(tokens, diagnostics);
    }

    public Hover getHover(List<String> textLines, int line, int col)
    {
        Scanner s = new Scanner(textLines);
        while (s.nextIgnore() && s.line <= line) {
            if (s.line != line || s.endCol <= col) {
                continue;
            }
            if (s.startCol > col) {
                break;
            }

            String message = "Type: " + s.tokenType;
            if (!s.tokenModifiers.isEmpty()) {
                message += ", Modifiers: " + String.join(", ", s.tokenModifiers);
            }

            var hover = new Hover(Either.forLeft(message));
            hover.setRange(new Range(
                new Position(s.line, s.startCol),
                new Position(s.line, s.endCol)
            ));
            return hover;
        }

        return null;
    }


    private static class Scanner
    {
        private final List<String> textLines;

        private String lockedTokenType = null;
        private final Set<String> lockedTokenModifiers = new HashSet<>();

        /**
         * Current line number. 0-based.
         */
        int line = 0;
        /**
         * Start position on the line. 0-based.
         */
        int startCol = 0;
        /**
         * End position on the line. 0-based, exclusive.
         */
        int endCol = 0; // exclusive
        String tokenType = null;
        final Set<String> tokenModifiers = new HashSet<>();
        boolean isBasedOnLockedToken = false;

        private Integer allModifiersStartCol = null;
        private String allModifiersTokenType = null;

        Scanner(List<String> textLines)
        {
            this.textLines = textLines;
        }

        /**
         * Yields the next token. The actual token information is made available
         * through various {@code Scanner} properties.
         *
         * <p>Opposed to {@link Scanner#next()}, this will not throw an
         * {@link PrematureLineEndingException}.
         *
         * @return True: A new token was parsed. False: Nothing more to parse.
         */
        public boolean nextIgnore()
        {
            while (true) {
                try {
                    return next();
                } catch (PrematureLineEndingException e) {
                    // Nothing
                }
            }
        }

        /**
         * Yields the next token. The actual token information is made available
         * through various {@code Scanner} properties.
         *
         * @return True: A new token was parsed. False: Nothing more to parse.
         * @throws PrematureLineEndingException If an all-modifier-combinations sections is parsed but
         *                                      cut short by a premature line ending
         */
        public boolean next() throws PrematureLineEndingException
        {
            clear();
            for (; line < textLines.size(); line++) {
                String textLine = textLines.get(line);
                for (int j = endCol; j < textLine.length(); j++) {
                    if (allModifiersStartCol != null) {
                        // Providing token for one part of an all-modifier-combinations section
                        if (j - allModifiersStartCol < ALL_MODIFIER_COMBINATIONS.size()) {
                            if (j < allModifiersStartCol) {
                                continue;
                            }
                            tokenType = allModifiersTokenType;
                            tokenModifiers.clear();
                            tokenModifiers.addAll(ALL_MODIFIER_COMBINATIONS.get(j - allModifiersStartCol));
                            startCol = j;
                            j++;
                            endCol = j;
                            return true;
                        }
                        // Done with all combinations, let's continue normally
                        allModifiersStartCol = null;
                    }

                    if (!Character.isAlphabetic(textLine.charAt(j))) {
                        continue;
                    }

                    // Consume type
                    startCol = j;
                    for (; j < textLine.length() && Character.isAlphabetic(textLine.charAt(j)); j++) ;
                    endCol = j;

                    tokenType = ALL_TOKEN_TYPES_LOWERCASE.get(
                        textLine.substring(startCol, endCol).toLowerCase()
                    );
                    tokenModifiers.clear();
                    isBasedOnLockedToken = false;

                    if (tokenType == null) {
                        if (lockedTokenType == null) {
                            continue;
                        }
                        // We have a locked token type & modifier, let's go ahead
                        // and see if we can parse a modifier below
                        tokenType = lockedTokenType;
                        tokenModifiers.addAll(lockedTokenModifiers);
                        isBasedOnLockedToken = true;
                        endCol = j = startCol;
                    } else if (j < textLine.length() && textLine.charAt(j) == ALL_MODIFIERS_OPERATOR) {
                        // This is an all-modifier-combinations section
                        allModifiersTokenType = tokenType;
                        j++;
                        allModifiersStartCol = j;
                        // The main token before '|'
                        return true;
                    }

                    // Consume modifiers
                    while (j < textLine.length() && (isModifierSeparator(textLine.charAt(j)) || isBasedOnLockedToken)) {
                        if (!isBasedOnLockedToken || endCol != startCol) {
                            // I.e. Opposite of: parsing first modifier when having a locked token
                            j++;
                        }
                        int modifierStart = j;
                        for (; j < textLine.length() && Character.isAlphabetic(textLine.charAt(j)); j++) ;
                        int modifierEnd = j; // exclusive

                        if (
                            modifierEnd == modifierStart
                            && Character.isWhitespace(textLine.charAt(j))
                            && !isBasedOnLockedToken
                        ) {
                            // Ending in modifier separator followed by whitespace
                            // - lock this token type & modifier. I.e. it will be
                            // applied to all subsequent modifier-only tokens.
                            lockedTokenType = tokenType;
                            lockedTokenModifiers.clear();
                            lockedTokenModifiers.addAll(tokenModifiers);
                        }

                        String tokenModifier = ALL_TOKEN_MODIFIERS_LOWERCASE.get(
                            textLine.substring(modifierStart, modifierEnd).toLowerCase()
                        );
                        if (tokenModifier == null) {
                            j = endCol; // Rewind
                            break;
                        }

                        tokenModifiers.add(tokenModifier);
                        endCol = modifierEnd;
                    }

                    if (isBasedOnLockedToken && endCol == startCol) {
                        // locked type and no modifier parsed - skip
                        continue;
                    }
                    return true;
                }
                // Line end reached
                if (allModifiersStartCol != null) {
                    if (textLine.length() < allModifiersStartCol + ALL_MODIFIER_COMBINATIONS.size()) {
                        allModifiersStartCol = null;
                        throw new PrematureLineEndingException(line, textLine.length());
                    }
                }
                allModifiersStartCol = null;
                startCol = endCol = 0;
            }

            // Nothing more to parse
            clear();
            return false;
        }

        private void clear()
        {
            tokenType = null;
            tokenModifiers.clear();
            isBasedOnLockedToken = false;
        }

        private boolean isModifierSeparator(char c)
        {
            for (char ms : MODIFIER_SEPARATORS) {
                if (c == ms) {
                    return true;
                }
            }
            return false;
        }
    }


    /**
     * @param tokens The format in which semantic tokens are encoded in a
     *               semanticTokens response
     */
    public record Result(List<Integer> tokens, List<Diagnostic> diagnostics) {}

    public static class PrematureLineEndingException extends Exception
    {
        public final int line;
        public final int col;

        PrematureLineEndingException(int line, int col)
        {
            super();
            this.line = line;
            this.col = col;
        }
    }
}
