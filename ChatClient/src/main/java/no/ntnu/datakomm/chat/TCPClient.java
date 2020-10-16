package no.ntnu.datakomm.chat;

import java.io.*;
import java.net.*;
import java.util.LinkedList;
import java.util.List;

public class TCPClient {
    private PrintWriter toServerWriter;
    private BufferedReader fromServerReader;
    private Socket connection;
    private String serverIP;


    // Hint: if you want to store a message for the last error, store it here
    private String lastError = null;

    private final List<ChatListener> listeners = new LinkedList<>();

    /**
     * Prints logMsg to console with formatting: "# TCPClientLog: " + logMsg
     * @param logMsg String to print.
     */
    private void log(String logMsg) {
        System.out.println("# TCPClientLog: " + logMsg);
    }

    /**
     * Connect to a chat server.
     *
     * @param host host name or IP address of the chat server
     * @param port TCP port of the chat server
     * @return True on success, false otherwise
     */
    public boolean connect(String host, int port) {
        try {
            connection = new Socket(host, port);
            connection.setKeepAlive(true);
            toServerWriter = new PrintWriter(connection.getOutputStream(), true);
            fromServerReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            serverIP = connection.getInetAddress().toString();
            log("Connected to server: '" + serverIP + "' at port: " + port);

            return true; // connection and writer/reader creation successful
        } catch (IOException e) {
            log("Connection Exception: " + e.getMessage());
            return false; // connection failed
        }
        // TODO Step 1: implement this method
        // Hint: Remember to process all exceptions and return false on error
        // Hint: Remember to set up all the necessary input/output stream variables

    }

    /**
     * Close the socket. This method must be synchronized, because several
     * threads may try to call it. For example: When "Disconnect" button is
     * pressed in the GUI thread, the connection will get closed. Meanwhile, the
     * background thread trying to read server's response will get error in the
     * input stream and may try to call this method when the socket is already
     * in the process of being closed. with "synchronized" keyword we make sure
     * that no two threads call this method in parallel.
     */
    public synchronized void disconnect() {
        // TODO Step 4: implement this method
        // Hint: remember to check if connection is active
        if (isConnectionActive()) {
            try {
                connection.close();
                connection = null;
                onDisconnect(); // Notify listeners of event
               log("Connection closed.");
            } catch (IOException e) {
                log("Disconnect Exception: " + e.getMessage());
            }
        }
    }

    /**
     * @return true if the connection is active (opened), false if not.
     */
    public boolean isConnectionActive() {
        return (connection != null && connection.isConnected());
    }

    /**
     * Send a command to server.
     *
     * @param cmd A command. It should include the command word and optional attributes, according to the protocol.
     * @return true on success, false otherwise
     */
    private boolean sendCommand(String cmd) {

        if (isConnectionActive()){
            toServerWriter.println(cmd);
            return true;

            // TODO Step 2: Implement this method
            // Hint: Remember to check if connection is active
        }
        else {
            log("Failed to send command. Is connection active?");
            return false;
        }
    }

    /**
     * Send a public message to all the recipients.
     *
     * @param message Message to send
     * @return true if message sent, false on error
     */
    public boolean sendPublicMessage(String message) {
        if (isConnectionActive()) {
           return sendCommand("msg " + message);

        } else {
            log("Error sending message. Is connection open?");
            return false;
        }
        // TODO Step 2: implement this method

        // Hint: Reuse sendCommand() method
        // Hint: update lastError if you want to store the reason for the error.
    }

    /**
     * Send a private message to a single recipient.
     *
     * @param recipient username of the chat user who should receive the message
     * @param message   Message to send
     * @return true if message sent, false on error
     */
    public boolean sendPrivateMessage(String recipient, String message) {
        if (isConnectionActive()) {
            return (sendCommand("privmsg " + recipient + " " + message));
        } else {
            log("Error sending private message. Is connection open?");
            return false;
        }

        // TODO Step 6: Implement this method
        // Hint: Reuse sendCommand() method
        // Hint: update lastError if you want to store the reason for the error.
    }

    /**
     * Send a login request to the chat server.
     *
     * @param username Username to use
     */
    public void tryLogin(String username) {

        if (isConnectionActive()) {
            sendCommand("login " + username);

        } else if (!isConnectionActive()) {
            log("No connection active, cannot log in.");
        } else {
            log("Could not log in. Username is invalid format.");
        } // TODO remove logging if unnecessary
        // TODO Step 3: implement this method
        // Hint: Reuse sendCommand() method
    }

    /**
     * Send a request for latest user list to the server. To get the new users,
     * clear your current user list and use events in the listener.
     */
    public void refreshUserList() {
        if (isConnectionActive()) {
            sendCommand("users");
        } else log("No connection active, cannot query user list.");
        // TODO Step 5: implement this method
        // Hint: Use Wireshark and the provided chat client reference app to find out what commands the
        // client and server exchange for user listing.
    }


    /**
     * Send a request for the list of commands that server supports.
     */
    public void askSupportedCommands() {
        if (isConnectionActive()) {
            sendCommand("help");
        } else log("No connection active. Cannot query server for supported commands.");
        // TODO Step 8: Implement this method
        // Hint: Reuse sendCommand() method
    }


    /**
     * Wait for chat server's response
     *
     * @return one line of text (one command) received from the server.
     */
    private String waitServerResponse() {
        String resp = null;
        if (isConnectionActive()) {
            try {
               if (fromServerReader.ready())
                   resp = fromServerReader.readLine();
            } catch (IOException e) {
                log("Response Exception: " + e.getMessage());
                log("Disconnecting socket due to server response error.");
                this.disconnect();
            }
        }
    return resp;
        // TODO Step 3: Implement this method
        // TODO Step 4: If you get I/O Exception or null from the stream, it means that something has gone wrong
        // with the stream and hence the socket. Probably a good idea to close the socket in that case.
    }

    /**
     * Get the last error message
     *
     * @return Error message or "" if there has been no error
     */
    public String getLastError() {
        if (lastError != null) {
            return lastError;
        } else {
            return "";
        }
    }

    /**
     * Start listening for incoming commands from the server in a new CPU thread.
     */
    public void startListenThread() {
        // Call parseIncomingCommands() in the new thread.
        Thread t = new Thread(this::parseIncomingCommands);
        t.start();
    }

    /**
     * Read incoming messages one by one, generate events for the listeners. A loop that runs until
     * the connection is closed.
     */
    private void parseIncomingCommands() {
        while (isConnectionActive()) {

            String response = waitServerResponse();

            if (response != null) {

                // split response into command and message parts
                String[] respSplit = response.split(" ", 2);
                // server response command is first word in response string
                String command = respSplit[0];
                // message is the remainder of the response, or null if response had no message
                String message = (respSplit.length > 1) ? respSplit[1] : null;
                // could be set to empty string instead of null

                switch (command) {
                    case "loginok":
                        onLoginResult(true, message); // message == null here, but signature demands it
                        break;

                    case "loginerr":
                        onLoginResult(false, message);
                        break;

                    case "cmderr":
                        lastError = message;
                        onCmdError(message);
                        break;

                    case "msgerror":
                        lastError = message;
                        onMsgError(message);
                        break;

                    case "msg":
                        if (message != null) {
                            String[] msgAsArray = message.split(" ", 2);
                            onMsgReceived(false, msgAsArray[0], msgAsArray[1]);
                        }
                        break;

                    case "privmsg":
                        if (message != null) {
                            String[] privMsgAsArray = message.split(" ", 2);
                            onMsgReceived(true, privMsgAsArray[0], privMsgAsArray[1] );
                        }
                        break;

                    case "msgok":
                        //TODO add action here?
                        break;

                    case "users":
                        if (message != null) {
                            String[] usersArray = message.split(" ");
                            onUsersList(usersArray);
                        }
                        break;

                    case "supported":
                       if (message != null) {
                           String[] supportedCmd = message.split(" ");
                           onSupported(supportedCmd);
                       }
                        break;

                    default: // we don't know what happened.
                        log("Unexpected command case: " +
                                "\n\tServer command: " + command +
                                "\n\tServer message: " + message);
                        break;

                }// switch
            }// if
        }// while

        // TODO Step 3: Implement this method
        // Hint: Reuse waitServerResponse() method
        // Hint: Have a switch-case (or other way) to check what type of response is received from the server
        // and act on it.
        // Hint: In Step 3 you need to handle only login-related responses.
        // Hint: In Step 3 reuse onLoginResult() method

        // TODO Step 5: update this method, handle user-list response from the server
        // Hint: In Step 5 reuse onUserList() method

        // TODO Step 7: add support for incoming chat messages from other users (types: msg, privmsg)
        // TODO Step 7: add support for incoming message errors (type: msgerr)
        // TODO Step 7: add support for incoming command errors (type: cmderr)
        // Hint for Step 7: call corresponding onXXX() methods which will notify all the listeners

        // TODO Step 8: add support for incoming supported command list (type: supported)

    } // parseIncomingCommands

    /**
     * Register a new listener for events (login result, incoming message, etc)
     *
     * @param listener The ChatListener listener to register.
     */
    public void addListener(ChatListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Unregister an event listener
     *
     * @param listener The ChatListener listener to unregister.
     */
    public void removeListener(ChatListener listener) {
        listeners.remove(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    // The following methods are all event-notificators - notify all the listeners about a specific event.
    // By "event" here we mean "information received from the chat server".
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Notify listeners that login operation is complete (either with success or
     * failure)
     *
     * @param success When true, login successful. When false, it failed
     * @param errMsg  Error message if any
     */
    private void onLoginResult(boolean success, String errMsg) {
        for (ChatListener l : listeners) {
            l.onLoginResult(success, errMsg);
        }
    }

    /**
     * Notify listeners that socket was closed by the remote end (server or
     * Internet error)
     */
    private void onDisconnect() {
        for (ChatListener l : listeners) {
            l.onDisconnect();
        }
        // TODO Step 4: Implement this method
        // Hint: all the onXXX() methods will be similar to onLoginResult()
    }

    /**
     * Notify listeners that server sent us a list of currently connected users
     *
     * @param users List with usernames
     */
    private void onUsersList(String[] users) {
        for (ChatListener l : listeners) {
            l.onUserList(users);
        }
        // TODO Step 5: Implement this method
    }

    /**
     * Notify listeners that a message is received from the server
     *
     * @param priv   When true, this is a private message
     * @param sender Username of the sender
     * @param text   Message text
     */
    private void onMsgReceived(boolean priv, String sender, String text) {
        for (ChatListener l : listeners) {
            l.onMessageReceived(new TextMessage(sender, priv, text));
        } // fixme: should this really be creating new TextMessage object? See class diagram in specification doc.
        // TODO Step 7: Implement this method
    }

    /**
     * Notify listeners that our message was not delivered
     *
     * @param errMsg Error description returned by the server
     */
    private void onMsgError(String errMsg) {
        log("Message error: " + errMsg);
        for (ChatListener l : listeners) {
            l.onMessageError(errMsg);
        }
        // TODO Step 7: Implement this method
    }

    /**
     * Notify listeners that command was not understood by the server.
     *
     * @param errMsg Error message
     */
    private void onCmdError(String errMsg) {
        for (ChatListener l : listeners) {
            l.onCommandError(errMsg);
        }
        // TODO Step 7: Implement this method
    }

    /**
     * Notify listeners that a help response (supported commands) was received
     * from the server
     *
     * @param commands Commands supported by the server
     */
    private void onSupported(String[] commands) {
        for (ChatListener l : listeners) {
            l.onSupportedCommands(commands);
        }
        // TODO Step 8: Implement this method
    }
}
