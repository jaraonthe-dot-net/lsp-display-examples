package net.jaraonthe.java.lsp_display_examples;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.*;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * This contains the main logic and plumbing together of the language server's
 * business logic.
 *
 * @author Jakob Rathbauer <jakob@jaraonthe.net>
 */
public class ExamplesLanguageServer
    implements LanguageServer, LanguageClientAware
{
    private static final Logger log = LoggerFactory.getLogger(ExamplesLanguageServer.class);

    private final Settings settings;
    public final ExecutorService executor = Executors.newCachedThreadPool();
    private final ExamplesTextDocumentService textService = new ExamplesTextDocumentService(this);

    private LanguageClient client;
    private Future<Void> listeningFuture;
    private InitializeParams params;
    private ServerCapabilities serverCapabilities;

    public ExamplesLanguageServer(Settings settings)
    {
        this.settings = settings;
    }

    @Override
    public void connect(LanguageClient client)
    {
        if (this.client != null) {
            throw new RuntimeException("Client is already connected");
        }
        this.client = client;
    }

    /**
     * Sets the Future the Launcher produced. Canceling this Future stops the
     * language server implementation.
     *
     * @param listeningFuture As produced by LSPLauncher.startListening()
     */
    public void setListeningFuture(Future<Void> listeningFuture) {
        this.listeningFuture = listeningFuture;
    }

    LanguageClient client() {
        if (this.client == null) {
            throw new RuntimeException("client isn't set yet");
        }
        return this.client;
    }

    InitializeParams params() {
        if (this.params == null) {
            throw new RuntimeException("params aren't set yet");
        }
        return this.params;
    }


    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params)
    {
        log.debug(">> initialize: {}", params);
        this.params = params;

        createCapabilities(params);
        var result = new InitializeResult(
            serverCapabilities,
            new ServerInfo("LSP Display Examples")
        );
        log.debug("<<- initialize: {}", result);

        return CompletableFuture.completedFuture(result);
    }

    @Override
    public void initialized(InitializedParams params) {
        log.debug(">> initialized: {}", params);
    }

    @Override
    public CompletableFuture<Object> shutdown()
    {
        log.debug(">> shutdown");
        // Nothing to do
        return CompletableFuture.completedFuture(new Object());
    }

    @Override
    public void exit()
    {
        log.debug(">> exit");
        if (listeningFuture == null) {
            throw new RuntimeException("listeningFuture isn't set yet");
        }
        listeningFuture.cancel(true);
    }

    /**
     * Should be called when the server process exits. This releases any
     * resources this class owns, e.g. background threads, open files.
     */
    public void tearDown()
    {
        executor.shutdownNow();
    }

    @Override
    public TextDocumentService getTextDocumentService()
    {
        return textService;
    }

    @Override
    public WorkspaceService getWorkspaceService()
    {
        return new WorkspaceService()
        {
            @Override
            public void didChangeConfiguration(DidChangeConfigurationParams params)
            {
                log.debug(">> didChangeConfiguration: {}", params);
            }

            @Override
            public void didChangeWatchedFiles(DidChangeWatchedFilesParams params)
            {
                log.debug(">> didChangeWatchedFiles: {}", params);
            }
        };
    }

    /**
     * Creates the server capabilities that are returned to the client upon
     * initialized(). Sets {@code this.serverCapabilities}.
     *
     * @param params for convenience
     */
    private void createCapabilities(InitializeParams params) {
        var c = new ServerCapabilities();

        c.setPositionEncoding(PositionEncodingKind.UTF16);

        var tdso = new TextDocumentSyncOptions();
        tdso.setOpenClose(true);
        tdso.setChange(TextDocumentSyncKind.Incremental);
        tdso.setWillSave(false);
        tdso.setWillSaveWaitUntil(false);
        tdso.setSave(false);
        c.setTextDocumentSync(tdso);

        var clientTdc = params.getCapabilities().getTextDocument();

        // Semantic Tokens
        c.setSemanticTokensProvider(new SemanticTokensWithRegistrationOptions(
            new SemanticTokensLegend(
                // We provide all token types the client supports
                // (Note: ExamplesTokenizer relies on this fact to warn about
                // unknown client token types)
                clientTdc.getSemanticTokens().getTokenTypes(),
                clientTdc.getSemanticTokens().getTokenModifiers()
            ),
            new SemanticTokensServerFull(false), // delta
            false // range
        ));

        if (clientTdc.getPublishDiagnostics() == null) {
            log.warn("Client does not support publishDiagnostics feature");
            return;
        }

        // Code Actions
        if (clientTdc.getCodeAction() != null) {
            c.setCodeActionProvider(true);
        } else {
            log.warn("Client does not support codeAction feature");
        }

        // Inlay hints
        if (clientTdc.getInlayHint() != null) {
            c.setInlayHintProvider(true);
        } else {
            log.warn("Client does not support inlayHint feature");
        }

        this.serverCapabilities = c;
        textService.setTokenizer(new ExamplesTokenizer(serverCapabilities));
    }
}
