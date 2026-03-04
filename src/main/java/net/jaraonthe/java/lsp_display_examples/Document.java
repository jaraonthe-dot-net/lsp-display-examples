package net.jaraonthe.java.lsp_display_examples;

import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Represents one file currently owned by (i.e. opened in) the LSP Client.
 *
 * <p>You can synchronize on this object to ensure it is not modified while performing an atomic
 * operation.
 *
 * @author Jakob Rathbauer <jakob@jaraonthe.net>
 */
public class Document
{
    /**
     * End-of-line sequences defined by LSP.
     */
    private static final Pattern EOL_REGEX = Pattern.compile("(\\r\\n|\\n|\\r)");

    private final String uri;

    private int version;
    /**
     * The document text split into individual lines, using LSP end-of-line sequences. The eol
     * sequences themselves are removed from these strings.
     */
    private List<String> textLines;
    private String fullTextCached = null;

    private Document(String uri, int version, String text)
    {
        this.uri = uri;
        this.version = version;
        this.initTextLines(text);
        this.fullTextCached = text;
    }

    /**
     * Creates a new lsp document based on the data provided by the client.
     *
     * @param tdi as provided by the LSP didOpen request
     */
    public Document(TextDocumentItem tdi)
    {
        this(tdi.getUri(), tdi.getVersion(), tdi.getText());
    }

    public String getUri()
    {
        return uri;
    }

    /**
     * Returns this document's current LSP version.
     */
    public synchronized int getVersion()
    {
        return version;
    }

    /**
     * Returns a list of this document's text lines.
     */
    public synchronized List<String> getTextLines()
    {
        return List.copyOf(textLines);
    }

    /**
     * Returns this document's current text.
     */
    public synchronized String getText()
    {
        if (fullTextCached == null) {
            fullTextCached = String.join("\n", textLines);
        }
        return fullTextCached;
    }

    /**
     * Updates this document's version and text content.
     */
    public synchronized void change(
        int newVersion,
        List<TextDocumentContentChangeEvent> contentChanges
    )
    {
        if (newVersion <= this.version) {
            throw new RuntimeException(
                "Cannot change LSP document to version " + newVersion
                    + " as current version is already " + this.version
            );
        }

        this.version = newVersion;
        if (contentChanges.isEmpty()) {
            return;
        }

        for (var change : contentChanges) {
            fullTextCached = null;
            var range = change.getRange();

            if (range == null) {
                // Change replaces entire document
                initTextLines(change.getText());
                fullTextCached = change.getText();
                continue;
            }

            // Character offsets count UTF-16 words (see server capabilities)
            int startLine = normalizeLineOffset(range.getStart().getLine());
            String startLineText = textLines.get(startLine);
            int startCharacter = normalizeCharacterOffset(range.getStart().getCharacter(), startLineText);

            int endLine = normalizeLineOffset(range.getEnd().getLine());
            String endLineText = textLines.get(endLine);
            int endCharacter = normalizeCharacterOffset(range.getEnd().getCharacter(), endLineText);

            if (endLine < startLine || (endLine == startLine && endCharacter < startCharacter)) {
                // Ignore invalid combination
                continue;
            }

            var newTextLines = Arrays.asList(splitLines(
                startLineText.substring(0, startCharacter) + change.getText()
                + endLineText.substring(endCharacter)
            ));

            if (endLine == startLine && newTextLines.size() == 1) {
                // Shortcut
                textLines.set(startLine, newTextLines.getFirst());
                continue;
            }

            textLines.subList(startLine, endLine + 1).clear();
            textLines.addAll(startLine, newTextLines);
        }
    }


    private int normalizeLineOffset(int lineOffset)
    {
        return Math.clamp(lineOffset, 0, textLines.size() - 1);
    }

    private int normalizeCharacterOffset(int characterOffset, String textLine)
    {
        // According to LSP spec, character offsets that are too large shall be
        // interpreted as the maximum value for the resp. text line.
        return Math.clamp(characterOffset, 0, textLine.length());
    }

    private String[] splitLines(String text)
    {
        return EOL_REGEX.split(text, -1);
    }

    private void initTextLines(String fullText)
    {
        // Note: We don't distinguish between the different line endings; and as
        //       long as this server doesn't make editing suggestions to the
        //       client (which may use different eol sequences than the user)
        //       this should be fine.
        textLines = new ArrayList<>(Arrays.asList(splitLines(fullText)));
    }
}
