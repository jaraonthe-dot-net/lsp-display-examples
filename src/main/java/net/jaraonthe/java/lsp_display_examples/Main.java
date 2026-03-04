package net.jaraonthe.java.lsp_display_examples;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * @author Jakob Rathbauer <jakob@jaraonthe.net>
 */
public class Main
{
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static final String HELP_TEXT =
        """
            Available options:
                -p <number>
                --port <number>
                    Communicate via TCP on the given port
                -h
                --help
                    to display help
                --about
                    to display credits and legal information""";

    public static final String ABOUT_TEXT =
        """
            LSP Display Examples
            
            Created by Jakob Rathbauer <jakob@jaraonthe.net> in Spring 2026.
            
            License (GPL v3):
            
            This program is free software: you can redistribute it and/or modify
            it under the terms of the GNU General Public License as published by
            the Free Software Foundation, either version 3 of the License, or
            (at your option) any later version.
            
            This program is distributed in the hope that it will be useful,
            but WITHOUT ANY WARRANTY; without even the implied warranty of
            MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
            GNU General Public License for more details.
            
            You should have received a copy of the GNU General Public License
            along with this program.  If not, see <http://www.gnu.org/licenses/>.""";

    public static void main(String[] args)
    {
        Settings settings;
        try {
            settings = Settings.fromArgs(args);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            System.exit(1);
            return;
        }

        int exitCode = 0;
        switch (settings.mode()) {
            case HELP:
                System.out.println(Main.HELP_TEXT);
                break;

            case ABOUT:
                System.out.println(Main.ABOUT_TEXT);
                break;

            case MAIN:
                exitCode = runServer(settings);
                break;
        }

        System.exit(exitCode);
    }

    private static int runServer(Settings settings) {
        var exitCode = 0;
        try {
            if (settings.port() == null) {
                PrintStream out = System.out;
                // Redirect all further outputs to err
                System.setOut(System.err);
                serveClient(System.in, out, settings);
            } else {
                try (ServerSocket serverSocket = new ServerSocket(settings.port())) {
                    log.info("Started Examples language server on port {}", serverSocket.getLocalPort());

                    Socket socket = serverSocket.accept();
                    log.info(
                        "Connection established with {}:{}",
                        socket.getInetAddress().getHostAddress(),
                        socket.getPort()
                    );
                    serveClient(socket.getInputStream(), socket.getOutputStream(), settings);
                }
            }

        } catch (IOException | InterruptedException | ExecutionException e) {
            log.error(e.toString());
            exitCode = 1;
        }

        log.info("Server stopped.");
        return exitCode;
    }

    /**
     * Provides the actual language server functionality to a single client.
     *
     * @param in Input Stream for receiving messages from client
     * @param out Output Stream for sending messages to client
     * @param settings Application settings
     */
    private static void serveClient(InputStream in, OutputStream out, Settings settings)
        throws IOException, InterruptedException, ExecutionException
    {
        // According to https://github.com/eclipse-lsp4j/lsp4j/blob/main/documentation/README.md
        ExamplesLanguageServer server = new ExamplesLanguageServer(settings);
        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(
            server,
            in,
            out
        );
        server.connect(launcher.getRemoteProxy());
        Future<Void> future = launcher.startListening();

        server.setListeningFuture(future);
        try {
            future.get(); // Wait for listener to complete
            log.info("Client disconnected");
        } catch (CancellationException e) {
            // I.e. Future was cancelled by ExamplesLanguageServer
            log.info("Server disconnected");
        } finally {
            server.tearDown();
        }
    }
}
