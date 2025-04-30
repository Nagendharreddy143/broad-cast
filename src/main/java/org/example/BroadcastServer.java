package org.example;



import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap; // Using ConcurrentHashMap for better performance maybe

public class BroadcastServer extends WebSocketServer {

    // Stores active connections that have provided a username
    // Consider ConcurrentHashMap keys if HashSet has issues, but synchronizedSet should be okay.
    private final Set<WebSocket> clients = Collections.synchronizedSet(new HashSet<>());
    // Using ConcurrentHashMap for potentially better concurrent read/write performance than synchronizedMap
    private final Map<WebSocket, String> clientUsernames = new ConcurrentHashMap<>();

    public BroadcastServer(int port) {
        super(new InetSocketAddress(port));
        System.out.println("DEBUG: BroadcastServer initialized on port " + port);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String clientAddress = conn.getRemoteSocketAddress().getAddress().getHostAddress();
        System.out.println("DEBUG: Connection attempt from: " + clientAddress + " (" + conn.getResourceDescriptor() + ")");
        // Waiting for username message
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String username = clientUsernames.remove(conn); // Get username and remove mapping
        boolean wasActive = clients.remove(conn);      // Remove from active clients
        String clientDesc = (username != null) ? username : (conn.getRemoteSocketAddress() != null ? conn.getRemoteSocketAddress().getAddress().getHostAddress() : "unknown");

        System.out.println("DEBUG: onClose triggered for " + clientDesc + ". Was active: " + wasActive + ". Code: " + code + ". Reason: " + reason);

        if (username != null && wasActive) { // Only announce if they had provided a username and were active
            System.out.println(username + " has disconnected. Reason: " + reason + " (Code: " + code + ")");
            broadcastMessage(username + " has left the chat.", conn); // Exclude self from broadcast
        } else {
            System.out.println("DEBUG: Client from " + clientDesc + " disconnected (likely before providing username or already removed).");
        }
        System.out.println("DEBUG: Active clients after onClose: " + clients.size() + ", Usernames mapped: " + clientUsernames.size());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        String clientAddress = conn.getRemoteSocketAddress().getAddress().getHostAddress();
        System.out.println("DEBUG: onMessage received from " + clientAddress + " (" + conn.getResourceDescriptor() + "): '" + message + "'");

        // Check if this connection already has a username assigned
        if (!clientUsernames.containsKey(conn)) {
            // --- Handle Username ---
            System.out.println("DEBUG: Treating message as username attempt.");
            String username = message.trim();
            if (username.isEmpty()) {
//                System.out.println("DEBUG: Empty username attempt from " + clientAddress);
                conn.send("SERVER: Username cannot be empty. Please disconnect and reconnect with a valid username.");
                conn.close(1003, "Invalid username");
                return;
            }

            // Check if username is already taken (using ConcurrentHashMap's values())
            if (clientUsernames.containsValue(username)) {
//                System.out.println("DEBUG: Duplicate username attempt from " + clientAddress + ": " + username);
                conn.send("SERVER: Username '" + username + "' is already taken. Please disconnect and choose another.");
                conn.close(1008, "Username taken");
                return;
            }

            // Store username and add to active clients
            clientUsernames.put(conn, username);
            clients.add(conn); // NOW the client is considered active for broadcasts

//            System.out.println("DEBUG: Username '" + username + "' accepted for " + clientAddress + ". Added to active sets.");
//            System.out.println(username + " (" + clientAddress + ") has joined.");
//            System.out.println("DEBUG: Active clients: " + clients.size() + ", Usernames mapped: " + clientUsernames.size());


            // Send a welcome message to the new client
            conn.send("SERVER: Welcome, " + username + "! There are now " + clients.size() + " users online.");

            // Broadcast join message to others
            broadcastMessage(username + " has joined the chat.", conn); // Exclude self

        } else {
            // --- Handle Regular Chat Message ---
            String senderUsername = clientUsernames.get(conn);
            if (senderUsername == null) { // Should not happen if logic is correct, but safety check
                System.err.println("ERROR: Received message from connection without username in map! Conn: " + conn.getResourceDescriptor());
                conn.close(1011, "Internal server error - user mapping lost");
                return;
            }

//            System.out.println("DEBUG: Treating message as chat from '" + senderUsername + "'.");
            System.out.println(senderUsername + ": " + message); // Log to server console

            // Broadcast message to all *active* clients
            String broadcastMsg = senderUsername + ": " + message;
//            System.out.println("DEBUG: Broadcasting chat message: '" + broadcastMsg + "'");
            broadcastMessage(broadcastMsg, null); // Send to ALL active clients (including sender)
            // If you don't want sender to receive their own message back:
            // broadcastMessage(broadcastMsg, conn);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        String username = null;
        String clientIdentifier = "Unknown Client";

        if (conn != null) {
            username = clientUsernames.remove(conn); // Remove mapping on error
            clients.remove(conn);                 // Remove from active clients

            if (username != null) {
                clientIdentifier = username;
            } else if (conn.getRemoteSocketAddress() != null) {
                try {
                    clientIdentifier = conn.getRemoteSocketAddress().getAddress().getHostAddress();
                } catch (Exception e) { /* Ignore if address unavailable */ }
            }
            clientIdentifier += " (" + conn.getResourceDescriptor() + ")";
        }

        System.err.println("ERROR involving " + clientIdentifier + ": " + ex.getMessage());
        System.out.println("DEBUG: Active clients after onError: " + clients.size() + ", Usernames mapped: " + clientUsernames.size());

        // ex.printStackTrace(); // Uncomment for full stack trace if needed
    }

    @Override
    public void onStart() {
        setConnectionLostTimeout(100);
        System.out.println("Broadcast server started successfully on port " + getPort() + "!");
        System.out.println("Waiting for clients to connect...");
        System.out.println("DEBUG: Server onStart completed.");
    }

    /**
     * Broadcasts a message to all connected clients who have provided a username.
     * @param message The message to broadcast
     * @param exclude The client to exclude from broadcast (can be null)
     */
    public void broadcastMessage(String message, WebSocket exclude) {
        String excludeUsername = (exclude != null) ? clientUsernames.get(exclude) : "None";
        System.out.println("DEBUG: Broadcasting '" + message + "'. Excluding: " + excludeUsername);

        // Create a temporary list to avoid ConcurrentModificationException if iterating directly on synchronizedSet
        Set<WebSocket> clientsToSendTo;
        synchronized (clients) {
            clientsToSendTo = new HashSet<>(clients); // Copy the set
        }

        System.out.println("DEBUG: Copied " + clientsToSendTo.size() + " clients for broadcast iteration.");
        int sentCount = 0;
        for (WebSocket client : clientsToSendTo) {
            String targetUsername = clientUsernames.get(client); // For logging
            if (client == null) {
                System.out.println("DEBUG: Skipping null client in broadcast list.");
                continue;
            }
            // Check if client is still in the official map (it might have disconnected between copy and now)
            if (!clientUsernames.containsKey(client)) {
                System.out.println("DEBUG: Skipping client " + targetUsername + " as it's no longer in the username map.");
                continue;
            }

            if (client.isOpen() && !client.equals(exclude)) {
                System.out.println("DEBUG: Sending to: " + targetUsername + " (" + client.getResourceDescriptor() + ", Open: " + client.isOpen() + ")");
                client.send(message);
                sentCount++;
            } else {
                System.out.println("DEBUG: Skipping send to: " + targetUsername + " (" + client.getResourceDescriptor() + ", Open: " + client.isOpen() + ", Excluded: " + client.equals(exclude) + ")");
            }
        }
        System.out.println("DEBUG: Broadcast finished. Sent to " + sentCount + " clients.");
    }
}