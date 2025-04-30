package org.example;

import org.apache.commons.cli.*;
import java.io.IOException;
import java.util.Scanner; // Added for username input

public class BroadcastApplication {
    private static final int DEFAULT_PORT = 8887;

    public static void main(String[] args) {
        Options options = new Options();
        options.addOption("p", "port", true, "Port number to use (default: " + DEFAULT_PORT + ")");
        options.addOption("h", "host", true, "Host to connect to (default: localhost)");
        // Removed username from CLI options, will prompt instead
        options.addOption("help", false, "Print help message");

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;

        // Need a scanner for potential client username input
        Scanner consoleScanner = new Scanner(System.in);

        try {
            // Filter out the command ("start" or "connect") before parsing options
            if (args.length == 0) {
                printHelp(formatter, options);
                consoleScanner.close(); // Close scanner on exit
                return;
            }
            String command = args[0];
            String[] commandArgs = new String[args.length - 1];
            System.arraycopy(args, 1, commandArgs, 0, args.length - 1);

            cmd = parser.parse(options, commandArgs);

            if (cmd.hasOption("help")) {
                printHelp(formatter, options);
                consoleScanner.close(); // Close scanner on exit
                return;
            }

            int port = Integer.parseInt(cmd.getOptionValue("port", String.valueOf(DEFAULT_PORT)));
            String host = cmd.getOptionValue("host", "localhost");

            switch (command.toLowerCase()) { // Use lower case for command comparison
                case "start":
                    if (cmd.getArgs().length > 0) { // Check for unexpected arguments for 'start'
                        System.out.println("Warning: Extra arguments ignored for 'start' command.");
                    }
                    System.out.println("Starting broadcast server on port: " + port);
                    BroadcastServer server = new BroadcastServer(port);
                    server.start();
                    System.out.println("Server started successfully on port " + port);

                    // Add shutdown hook to close server gracefully
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        System.out.println("Shutting down server...");
                        try {
                            // Stop server, parameter is timeout in ms
                            server.stop(1000);
                        } catch (InterruptedException e) {
                            System.err.println("Server stop interrupted.");
                            Thread.currentThread().interrupt();
                        } catch (Exception e) { // Catch broader exceptions during stop
                            System.err.println("Error stopping server: " + e.getMessage());
                            e.printStackTrace();
                        } finally {
                            consoleScanner.close(); // Ensure scanner is closed on shutdown
                        }
                    }));
                    // Keep main thread alive for server (alternatively, server.run() could be used if it blocks)
                    // For this simple example, the shutdown hook handles cleanup.
                    // If server.start() returns immediately, we might need: Thread.currentThread().join();
                    // However, WebSocketServer usually manages its own threads.
                    break;

                case "connect":
                    // --- Prompt for Username ---
                    System.out.print("Enter your username: ");
                    String username = consoleScanner.nextLine().trim();
                    while (username.isEmpty()) {
                        System.out.println("Username cannot be empty.");
                        System.out.print("Enter your username: ");
                        username = consoleScanner.nextLine().trim();
                    }
                    // --- End Prompt for Username ---

                    System.out.println("Connecting to server at " + host + ":" + port + " as " + username);
                    // Pass username to the client constructor
                    BroadcastClient client = new BroadcastClient(host, port, username, consoleScanner);
                    client.connect(); // connect() will handle the rest, including closing scanner
                    break;

                default:
                    System.out.println("Unknown command: " + command);
                    printHelp(formatter, options);
                    break;
            }

        } catch (ParseException e) {
            System.err.println("Error parsing command line arguments: " + e.getMessage());
            printHelp(formatter, options);
        } catch (NumberFormatException e) {
            System.err.println("Error: Invalid port number provided.");
            printHelp(formatter, options);
        } catch (Exception e) { // Catch more general errors
            System.err.println("An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Close scanner if it wasn't passed to the client or if an early error occurred
            // The client is now responsible for closing the scanner it receives.
            // If the flow doesn't reach client creation/shutdown hook, close it here.
            // This is tricky. Let's rely on client/shutdown hook for now.
            // Consider adding try-with-resources for Scanner if appropriate later.
        }
    }

    private static void printHelp(HelpFormatter formatter, Options options) {
        formatter.printHelp("broadcast-server <command> [options]",
                "\nCommands:\n  start   Start the broadcast server\n  connect Connect to a server as client",
                options,
                "\nExample:\n  java -jar broadcast-server.jar start -p 8888\n  java -jar broadcast-server.jar connect -h somehost -p 8888");
    }
}