package net.jaraonthe.java.lsp_display_examples;

import org.eclipse.lsp4j.*;
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
    public static final List<String> ALL_TOKEN_TYPES;
    public static final List<String> ALL_TOKEN_MODIFIERS;

    private static final Map<String, String> ALL_TOKEN_TYPES_LOWERCASE;
    private static final Map<String, String> ALL_TOKEN_MODIFIERS_LOWERCASE;

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
     * semantic token type and modifier.
     */
    public static String getExample(boolean withModifiers)
    {
        if (!withModifiers) {
            return String.join("\n", ALL_TOKEN_TYPES) + "\n";
        }

        int maxLength = ALL_TOKEN_TYPES.stream().map(String::length)
            .reduce(Math::max).orElse(0);

        return ALL_TOKEN_TYPES.stream().map((t) -> {
            String padding = " ".repeat(maxLength - t.length() + 2);
            return t + padding + ALL_TOKEN_MODIFIERS.stream().map((m) ->
                t + ":" + m
            ).collect(Collectors.joining(padding));
        }).collect(Collectors.joining("\n")) + "\n";
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
        int absoluteLine = 0;
        int deltaLine = 0;
        int previousStart = 0;

        for (String line : textLines) {
            for (int i = 0; i < line.length(); i++) {
                if (!Character.isAlphabetic(line.charAt(i))) {
                    continue;
                }

                // Consume type
                int start = i;
                for (; i < line.length() && Character.isAlphabetic(line.charAt(i)); i++);
                int end = i; // exclusive

                String typeKeyword = line.substring(start, end).toLowerCase();
                String tokenType = ALL_TOKEN_TYPES_LOWERCASE.get(typeKeyword);
                if (tokenType == null) {
                    continue;
                }
                Integer typeNumber = tokenTypesMap.get(tokenType);
                if (typeNumber == null) {
                    var d = new Diagnostic(
                        new Range(new Position(absoluteLine, start), new Position(absoluteLine, end)),
                        "Client does not support " + tokenType
                    );
                    d.setSeverity(DiagnosticSeverity.Warning);
                    diagnostics.add(d);
                    continue;
                }
                int modifiers = 0;

                // Consume modifiers
                while (i < line.length() && isModifierSeparator(line.charAt(i))) {
                    // Consume modifier word
                    i++;
                    int modifierStart = i;
                    for (; i < line.length() && Character.isAlphabetic(line.charAt(i)); i++);
                    int modifierEnd = i; // exclusive

                    String modifierKeyword = line.substring(modifierStart, modifierEnd).toLowerCase();
                    String tokenModifier = ALL_TOKEN_MODIFIERS_LOWERCASE.get(modifierKeyword);
                    if (tokenModifier == null) {
                        i = end; // Rewind
                        break;
                    }
                    Integer modifierFlag = tokenModifiersMap.get(tokenModifier);
                    if (modifierFlag == null) {
                        var d = new Diagnostic(
                            new Range(new Position(absoluteLine, modifierStart), new Position(absoluteLine, modifierEnd)),
                            "Client does not support " + tokenModifier
                        );
                        d.setSeverity(DiagnosticSeverity.Warning);
                        diagnostics.add(d);

                        i = end; // Rewind
                        break;
                    }

                    modifiers += modifierFlag;
                    end = modifierEnd;
                }

                // deltaLine, deltaStart, length, tokenType, tokenModifiers
                tokens.add(deltaLine);
                tokens.add(start - previousStart);
                tokens.add(end - start);
                tokens.add(typeNumber);
                tokens.add(modifiers);

                deltaLine = 0;
                previousStart = start;
            }

            absoluteLine++;
            deltaLine++;
            previousStart = 0;
        }

        return new Result(tokens, diagnostics);
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


    /**
     * @param tokens The format in which semantic tokens are encoded in a
     *               semanticTokens response
     */
    public record Result(List<Integer> tokens, List<Diagnostic> diagnostics) {}
}
