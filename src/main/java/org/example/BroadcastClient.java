package org.example;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit; // Import TimeUnit

public class BroadcastClient {
    private final String host;
    private final int port;
    private final String username;
    private WebSocketClient client;
    private final Scanner consoleScanner;
    private volatile boolean connected = false; // Flag to indicate successful connection

    public BroadcastClient(String host, int port, String username, Scanner scanner) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.consoleScanner = scanner;
        System.out.println("DEBUG: BroadcastClient created for " + username + " targeting " + host + ":" + port);
    }

    public void connect() {
        try {
            URI serverUri = new URI("ws://" + host + ":" + port);
            System.out.println("DEBUG: Client attempting to connect to: " + serverUri);

            client = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    System.out.println("DEBUG: Client onOpen triggered. Status: " + handshakedata.getHttpStatus() + ". Sending username.");
                    // Send username immediately
                    client.send(username);
                    connected = true; // Mark as connected
                    System.out.println(">>> Connected to server as " + username + ". You can now send messages.");
                }

                @Override
                public void onMessage(String message) {
                    // Print messages received from server
//                    System.out.println("DEBUG: Client onMessage received: '" + message + "'");
                    System.out.println(message); // Display message to user
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    connected = false; // Mark as disconnected
//                    System.out.println("DEBUG: Client onClose triggered. Code: " + code + ", Reason: " + reason + ", Remote: " + remote);
//                    System.out.println("<<< Connection closed" + (remote ? " by server" : "") +
//                            ": " + reason + " (Code: " + code + ")");
                    cleanupAndExit(0);
                }

                @Override
                public void onError(Exception ex) {
                    connected = false; // Mark connection failed
                    System.err.println("DEBUG: Client onError triggered: " + ex.getClass().getName() + " - " + ex.getMessage());
                    System.err.println("<<< Connection Error: " + ex.getMessage());
                    // ex.printStackTrace(); // Uncomment for detailed error
                    cleanupAndExit(1);
                }
            };

            // Use connectBlocking for simplicity in testing connection establishment
            System.out.println("DEBUG: Calling connectBlocking...");
            if (!client.connectBlocking(10, TimeUnit.SECONDS)) { // Increased timeout
                System.err.println("<<< Connection timed out after 10 seconds.");
                cleanupAndExit(1);
                return; // Exit if connection failed
            }
            System.out.println("DEBUG: connectBlocking finished. Client connected state: " + connected);


            // Only start the message thread if connection was successful (checked by connectBlocking and onOpen flag)
            if (!connected) {
                System.err.println("<<< Failed to establish connection or send initial username properly. Exiting.");
                cleanupAndExit(1);
                return;
            }


            // Start the thread to read messages from console
            Thread messageThread = new Thread(() -> {
                System.out.println("DEBUG: Client message input thread started.");
                // Scanner prompt already shown in BroadcastApplication
                while (connected && client.isOpen() && consoleScanner.hasNextLine()) { // Check connected flag and isOpen
                    String message = consoleScanner.nextLine();
                    System.out.println("DEBUG: Client read input: '" + message + "'");
                    if ("exit".equalsIgnoreCase(message.trim())) {
                        System.out.println("DEBUG: Client sending close request.");
                        client.close(); // Initiate close
                        break; // Exit input loop
                    }
                    // Ensure client is still open *before* sending
                    if (connected && client.isOpen() && !message.trim().isEmpty()) {
                        System.out.println("DEBUG: Client sending message: '" + message + "'");
                        client.send(message);
                    } else if (!message.trim().isEmpty()) {
                        System.out.println("DEBUG: Client skipped sending (Connected: " + connected + ", Open: " + client.isOpen() + ")");
                        if (!connected || !client.isOpen()) break; // Exit loop if disconnected
                    }
                }
                System.out.println("DEBUG: Client message input thread finished. Connected: " + connected + ", Open: " + client.isOpen());
                // Ensure client is closed if input loop exited for other reasons than 'exit' command
                if (connected && client.isOpen()) {
                    System.out.println("DEBUG: Input thread closing client connection.");
                    client.close();
                }
            });
            messageThread.setDaemon(true);
            messageThread.start();

            // Main thread waits implicitly because connectBlocking was used.
            // If we used connect(), we'd need a mechanism to keep the main thread alive
            // until onClose/onError calls cleanupAndExit.

        } catch (URISyntaxException e) {
            System.err.println("<<< Invalid server URI format: ws://" + host + ":" + port + " -> " + e.getMessage());
            cleanupAndExit(1);
        } catch (InterruptedException e) {
            System.err.println("<<< Client connection attempt interrupted.");
            Thread.currentThread().interrupt();
            cleanupAndExit(1);
        } catch (Exception e) {
            System.err.println("<<< Failed to start client: " + e.getClass().getName() + " - " + e.getMessage());
            // e.printStackTrace(); // Uncomment for detailed error
            cleanupAndExit(1);
        }
    }

    private void cleanupAndExit(int status) {
        System.out.println("DEBUG: cleanupAndExit called with status " + status);
        connected = false; // Ensure flag is false
        // Close WebSocket client if it exists and is not already closed/closing
        if (client != null && !client.isClosed() && !client.isClosing()) {
            try {
                System.out.println("DEBUG: Closing WebSocket client connection...");
                client.closeBlocking(); // Attempt graceful close
                System.out.println("DEBUG: WebSocket client closed.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("DEBUG: Interrupted while closing WebSocket.");
            } catch (Exception e) {
                System.err.println("DEBUG: Error closing WebSocket during cleanup: " + e.getMessage());
            }
        } else if (client != null) {
            System.out.println("DEBUG: WebSocket client already closed or closing.");
        }

        // Close scanner
        if (consoleScanner != null) {
            System.out.println("DEBUG: Closing console scanner...");
            consoleScanner.close();
        }

        System.out.println("<<< Exiting client (Status code: " + status + ")");
        System.exit(status);
    }
}