package net.jaraonthe.java.lsp_display_examples;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles document-related features of the language server.
 *
 * @author Jakob Rathbauer <jakob@jaraonthe.net>
 */
class ExamplesTextDocumentService implements TextDocumentService
{
    private static final Logger log = LoggerFactory.getLogger(ExamplesTextDocumentService.class);

    private final ExamplesLanguageServer server;
    private final Map<String, Document> openDocuments = new HashMap<>();

    private ExamplesTokenizer tokenizer;

    ExamplesTextDocumentService(ExamplesLanguageServer server) {
        this.server = server;
    }

    /**
     * Sets the tokenizer to use. As the tokenizer's configuration depends on
     * server capabilities, this shall be called once by the Server object upon
     * initializing connection with the LSP client.
     *
     * @param tokenizer A fully configured Tokenizer
     */
    void setTokenizer(ExamplesTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        log.debug(">> didOpen: {}", params);
        var document = new Document(params.getTextDocument());
        synchronized (openDocuments) {
            openDocuments.put(params.getTextDocument().getUri(), document);
        }
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        log.debug(">> didClose: {}", params);
        synchronized (openDocuments) {
            openDocuments.remove(params.getTextDocument().getUri());
        }
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        log.debug(">> didChange: {}", params);

        Document document = getDocument(params.getTextDocument().getUri());
        if (document == null) {
            return;
        }
        document.change(params.getTextDocument().getVersion(), params.getContentChanges());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        log.debug(">> didSave: {}", params);
        // Nothing (server capabilities currently don't support this)
    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        log.debug(">> semanticTokens/full: {}", params);

        return CompletableFuture.supplyAsync(() -> {
            Document document = getDocument(params.getTextDocument().getUri());
            if (document == null) {
                throw new ResponseErrorException(new ResponseError(
                    ResponseErrorCode.RequestFailed,
                    "Requested semantic tokens for a document that is not open.",
                    null
                ));
            }

            List<Integer> tokens;
            if (tokenizer != null) {
                List<String> textLines;
                int version;
                synchronized (document) {
                    textLines = document.getTextLines();
                    version = document.getVersion();
                }
                var result = tokenizer.getTokens(textLines);
                tokens = result.tokens();

                publishDiagnostics(result.diagnostics(), document.getUri(), version);
            } else {
                tokens = new ArrayList<>(0);
            }

            log.debug("<<- semanticTokens/full: <omitted>({} tokens)", tokens.size() / 5);
            return new SemanticTokens(tokens);
        });
    }

    private void publishDiagnostics(List<Diagnostic> diagnostics, String uri, int version) {
        if (server.params().getCapabilities().getTextDocument().getPublishDiagnostics() == null) {
            // Don't push diagnostics if client doesn't support it
            return;
        }

        var data = new PublishDiagnosticsParams(uri, diagnostics, version);
        log.debug("<< publishDiagnostics: {}", data);
        server.client().publishDiagnostics(data);
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params)
    {
        log.debug(">> codeAction: {}", params);
        return CompletableFuture.supplyAsync(() -> {
            List<Either<Command, CodeAction>> data = new ArrayList<>(1);

            // TODO Any code action?

            log.debug("<< codeAction: {}", data);
            return data;
        });
    }

    @Override
    public CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params)
    {
        log.debug(">> inlayHint: {}", params);
        return CompletableFuture.supplyAsync(() -> {
            List<InlayHint> data = new ArrayList<>(1);

            // Show "Generate Example" inlay hint if file is empty
            String uri = params.getTextDocument().getUri();
            if (getDocument(uri).getText().isBlank()) {
                data.add(createGenerateHint(
                    "(Generate example)",
                    ExamplesTokenizer.getExample(false)
                ));
                data.add(createGenerateHint(
                    "(Generate with modifiers)",
                    ExamplesTokenizer.getExample(true)
                ));
            }

            log.debug("<< inlayHint: {}", data);
            return data;
        });
    }

    private InlayHint createGenerateHint(String label, String generatedText)
    {
        var hint = new InlayHint(
            new Position(0, 0),
            Either.forLeft(label)
        );
        hint.setTextEdits(List.of(new TextEdit(
            new Range(new Position(0, 0), new Position(0, 0)),
            generatedText
        )));
        hint.setPaddingLeft(true);
        hint.setPaddingRight(true);

        return hint;
    }

    private Document getDocument(String uri) {
        synchronized (openDocuments) {
            return openDocuments.get(uri);
        }
    }
}
