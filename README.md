# Simple WebSocket Broadcast Chat

A basic command-line chat application demonstrating WebSocket communication using Java. It includes a server that broadcasts messages to all connected clients and clients that connect with a unique username.

This project utilizes the `Java-WebSocket` library for handling WebSocket connections and `Apache Commons CLI` for command-line argument parsing.

## Prerequisites

Before you begin, ensure you have the following installed:

* **Java Development Kit (JDK):** Version 11 or later.
* **Apache Maven:** Version 3.6.0 or later (for building the project).

## Building the Application

1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/Nagendharreddy143/broad-cast.git](https://github.com/Nagendharreddy143/broad-cast.git)
    cd broad-cast
    ```

2.  **Compile and package the application using Maven:**
    ```bash
    mvn clean package
    ```
    This command will compile the source code, run any tests, and create an executable JAR file (including dependencies) named `broadcast-server.jar` in the `target/` directory.

## Running the Application

The application JAR can run in two modes: `start` (to run the server) or `connect` (to run a client).

### 1. Starting the Server

Open a terminal or command prompt and run the following command:

```bash
java -jar target/broadcast-server.jar start [options]
Available Options:

-p <port> or --port <port>: Specifies the port number the server should listen on. (Default: 8887)
Examples:

Start the server on the default port (8887):
Bash

java -jar target/broadcast-server.jar start
Start the server on port 9000:
Bash

java -jar target/broadcast-server.jar start -p 9000
The server will print log messages indicating it has started and is waiting for clients.

2. Connecting as a Client
Open a new terminal or command prompt for each client you want to connect. Run the following command:

Bash

java -jar target/broadcast-server.jar connect [options]
Available Options:

-h <host> or --host <host>: Specifies the hostname or IP address of the server to connect to. (Default: localhost)
-p <port> or --port <port>: Specifies the port number the server is running on. (Default: 8887)
Examples:

Connect to the server running on localhost using the default port (8887):
Bash

java -jar target/broadcast-server.jar connect
Connect to a server running on a different machine (192.168.1.100) on port 9000:
Bash

java -jar target/broadcast-server.jar connect -h 192.168.1.100 -p 9000
After running the connect command:

You will be prompted to enter a username:
Enter your username:
Type your desired username and press Enter.
Once connected, you can type messages and press Enter to broadcast them to all other connected clients.
Type exit and press Enter to disconnect the client.
Project Structure
pom.xml: Maven project configuration, including dependencies.
src/main/java/com/example/: Contains the Java source code.
BroadcastApplication.java: Main entry point, parses command-line arguments, starts server or client.
BroadcastServer.java: Implements the WebSocket server logic using Java-WebSocket. Handles connections, messages, and broadcasting.
BroadcastClient.java: Implements the WebSocket client logic. Handles connection, sending/receiving messages, and user input.

This README should give users a good overview and clear steps to get your chat appli
