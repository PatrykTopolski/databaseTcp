import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class DatabaseNode {

    private static final String TCP_ADDRESS = "localhost";
    private int tcpPort;
    //can be changed later
    private Map<Integer, Integer> data = null;
    private volatile List<InetSocketAddress> connections;
    private List<UUID> uuids;
    private Thread runingThread;
    private volatile boolean running = true;
    private Socket socket;
    private int key;
    private ServerSocket server;
    private ExecutorService threadPool;

    public DatabaseNode(int tcpPort, int key, int value, List<InetSocketAddress> connections) {

        this.tcpPort = tcpPort;
        data = new HashMap<>();
        data.put(key, value);
        this.key = key;
        this.connections = connections;
        connectToNode();
        uuids = new ArrayList<>();
    }

    public void start() {
        try {
            server = new ServerSocket(tcpPort);
            threadPool = Executors.newCachedThreadPool();
            while (running) {
                Socket client = server.accept();
                threadPool.submit(() -> handleClient(client));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void connectToNode() {
        for (InetSocketAddress addr : connections) {
            // Connect to another node in the network
            try (Socket node = new Socket(addr.getAddress(), addr.getPort())) {
                // Send our current data to the connected node
                PrintWriter out = new PrintWriter(node.getOutputStream(), true);
                out.println("connect " + TCP_ADDRESS + ":" + tcpPort);
                System.out.println("connecting to : " + addr.getPort());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleClient(Socket client) {
        // Handle incoming requests from clients
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {
            String request = in.readLine();
            String[] parts = request.split(" ");
            String command = parts[0];
            if (command.equals("get-value")) {
                int key = Integer.parseInt(parts[1]);

                if (parts.length > 2) {
                    if (!uuids.contains(UUID.fromString(parts[2]))) {
                        uuids.add(UUID.fromString(parts[2]));
                        handleGetValueCommand(key, out, UUID.fromString(parts[2]));
                    } else {
                        out.close();
                        return;
                    }
                } else {
                    handleGetValueCommand(key, out, UUID.randomUUID());
                }
            } else if (command.equals("set-value")) {
                String[] dataParts = parts[1].split(":");
                int key = Integer.parseInt(dataParts[0]);
                int value = Integer.parseInt(dataParts[1]);
                if (parts.length > 2) {
                    if (!uuids.contains(UUID.fromString(parts[2]))) {
                        uuids.add(UUID.fromString(parts[2]));
                        handleUpdateValueCommand(key, value, out, UUID.fromString(parts[2]));
                    } else {
                        out.close();
                        return;
                    }
                } else {
                    handleUpdateValueCommand(key, value, out, UUID.randomUUID());
                }
            } else if (command.equals("find-key")) {
                int key = Integer.parseInt(parts[1]);
                if (parts.length > 2) {
                    if (!uuids.contains(UUID.fromString(parts[2]))) {
                        uuids.add(UUID.fromString(parts[2]));
                        System.out.println("asking to find key: " + key);
                        handleSearchCommand(key, out, UUID.fromString(parts[2]));
                    } else {
                        out.close();
                        return;
                    }
                } else {
                    handleSearchCommand(key, out, UUID.randomUUID());
                }
            } else if (command.equals("get-max")) {
                if (parts.length > 1) {
                    System.out.println(UUID.fromString(parts[1]));
                    if (!uuids.contains(UUID.fromString(parts[1]))) {
                        uuids.add(UUID.fromString(parts[1]));
                        handleGetMaxCommand(out, UUID.fromString(parts[1]), parts[2]);
                    } else {
                        out.close();
                        return;
                    }
                } else {
                    UUID uuid = UUID.randomUUID();
                    System.out.println(uuid);
                    handleGetMaxCommand(out, uuid, String.valueOf(tcpPort));
                }
            } else if (command.equals("get-min")) {
                if (parts.length > 1) {
                    if (!uuids.contains(UUID.fromString(parts[1]))) {
                        uuids.add(UUID.fromString(parts[1]));
                        handleGetMinCommand(out, UUID.fromString(parts[1]));
                    } else {
                        out.close();
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    handleGetMinCommand(out, UUID.randomUUID());
                }
            } else if (command.equals("terminate")) {
                out.println("OK");
                handleTerminateCommand();
            } else if (command.equals("terminating")) {
                System.out.println(connections.toString());
                String[] dataParts = parts[1].split(":");
                String host = dataParts[0];
                int port = Integer.parseInt(dataParts[1]);
                connections.removeIf(inetSocket -> inetSocket.getPort() == port && inetSocket.getHostName().contains(host));
                System.out.println(connections.toString());
            } else if (command.equals("connect")) {
                String[] dataParts = parts[1].split(":");
                connections.add(new InetSocketAddress(dataParts[0], Integer.parseInt(dataParts[1])));
                System.out.println(connections.toString());
            } else if (command.equals("new-record")) {
                String[] dataParts = parts[1].split(":");
                int key = Integer.parseInt(dataParts[0]);
                int value = Integer.parseInt(dataParts[1]);
                handleNewRecordCommand(key, value, out);
            } else {
                out.println("ERROR: Invalid command: " + command);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) {
        // Parse command line arguments
        int tcpPort = 0;
        int key = 0;
        int value = 0;
        List<InetSocketAddress> connections = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-tcpport")) {
                tcpPort = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-record")) {
                String[] parts = args[++i].split(":");
                key = Integer.parseInt(parts[0]);
                value = Integer.parseInt(parts[1]);
            } else if (args[i].equals("-connect")) {
                String[] parts = args[++i].split(":");
                InetSocketAddress inet = new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
                connections.add(inet);
            }
        }
        if (tcpPort == 0) {
            System.err.println("ERROR: Missing -tcpport argument");
            return;
        }
        // Create and start the node
        DatabaseNode node = new DatabaseNode(tcpPort, key, value, connections);
        System.out.println("my port is: " + tcpPort);
        node.start();
    }

    private void notifyTermination() {
        for (InetSocketAddress connection : connections) {
            try {
                Socket socket = new Socket(connection.getAddress(), connection.getPort());
                PrintWriter os = new PrintWriter(socket.getOutputStream());
                os.println("terminating " + TCP_ADDRESS + ":" + tcpPort);
                os.flush();
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleTerminateCommand() {
        running = false;
        notifyTermination();
        try {
            // Close the server socket to stop accepting new connections
            server.close();
            // Interrupt all threads in the thread pool
            threadPool.shutdownNow();
            // Wait for all threads to complete
            threadPool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void handleGetValueCommand(int key, PrintWriter out, UUID uuid) {
        if (data.containsKey(key)) {
            out.println(data.get(key));
        } else {
            // Search for the value in connected nodes
            for (InetSocketAddress connection : connections) {
                try {
                    Socket socket = new Socket(connection.getAddress(), connection.getPort());

                    PrintWriter os = new PrintWriter(socket.getOutputStream());
                    os.println(("get-value " + key) + " " + uuid);
                    if (find(out, socket, os)) return;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            out.println("ERROR");
        }
    }

    private void handleUpdateValueCommand(int key, int value, PrintWriter out, UUID uuid) {
        boolean updated = false;
        if (data.containsKey(key)) {
            data.put(key, value);
            out.println("OK");
            updated = true;
        } else {
            // Search for the value in connected nodes
            for (InetSocketAddress connection : connections) {
                try {
                    Socket socket = new Socket(connection.getAddress(), connection.getPort());

                    PrintWriter os = new PrintWriter(socket.getOutputStream());
                    os.println("set-value " + key + ":" + value + " " + uuid);
                    os.flush();

                    InputStream is = socket.getInputStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    String response = br.readLine();
                    if (response != null) {
                        if (response.equals("OK")) {
                            out.println("OK");
                            updated = true;
                            break;
                        }
                    }
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (!updated) {
            out.println("ERROR");
        }
    }

    private void handleSearchCommand(int key, PrintWriter out, UUID uuid) {
        if (data.containsKey(key)) {
            out.println(this.TCP_ADDRESS + ":" + this.tcpPort);
        } else {
            // Search for the key in connected nodes
            for (InetSocketAddress connection : connections) {
                try {
                    Socket socket = new Socket(connection.getAddress(), connection.getPort());

                    PrintWriter os = new PrintWriter(socket.getOutputStream());
                    os.println("find-key " + key + " " + uuid);
                    if (find(out, socket, os)) return;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            out.println("ERROR");
        }
    }

    private boolean find(PrintWriter out, Socket socket, PrintWriter os) throws IOException {
        os.flush();
        InputStream is = socket.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String returnMessage = br.readLine();
        if (returnMessage != null) {
            out.println(returnMessage);
            return true;
        }
        socket.close();
        return false;
    }


    private void handleGetMaxCommand(PrintWriter out, UUID uuid, String port) {
        int maxKey = -1;
        int maxValue = -1;
        // Check current node's data
        for (Map.Entry<Integer, Integer> entry : data.entrySet()) {
            if (entry.getValue() > maxValue) {
                maxKey = entry.getKey();
                maxValue = entry.getValue();
            }
        }

        // Keep track of nodes that have been visited to avoid infinite loops
        Set<InetSocketAddress> visitedNodes = new HashSet<>();
        visitedNodes.add(new InetSocketAddress(TCP_ADDRESS, tcpPort));
        Optional<InetSocketAddress> previous = connections.stream().filter(x -> x.getPort() == Integer.parseInt(port)).findFirst();
        if (previous.isPresent()) {
            System.out.println("adding previous port: " + port);
            visitedNodes.add(previous.get());
        }


        // Search for the max value in connected nodes
        for (InetSocketAddress connection : connections) {
            if (!visitedNodes.contains(connection)) {
                visitedNodes.add(connection);
                try {
                    Socket socket = new Socket(connection.getAddress(), connection.getPort());
                    PrintWriter os = new PrintWriter(socket.getOutputStream());
                    os.println("get-max " + uuid + " " + tcpPort);
                    os.flush();
                    InputStream is = socket.getInputStream();
                    System.out.println("im asking node: " + connection.getPort() + " for max value");
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    String response = br.readLine();

                    if (response != null) {
                        String[] parts = response.split(":");
                        int key = Integer.parseInt(parts[0]);
                        int value = Integer.parseInt(parts[1]);
                        if (value > maxValue) {
                            maxKey = key;
                            maxValue = value;
                        }
                    }
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        out.println(maxKey + ":" + maxValue);
        out.close();
    }



    private void handleGetMinCommand(PrintWriter out, UUID uuid) {
        int minKey = Integer.MAX_VALUE;
        int minValue = Integer.MAX_VALUE;
        // Check current node's data
        for (Map.Entry<Integer, Integer> entry : data.entrySet()) {
            if (entry.getValue() < minValue) {
                minKey = entry.getKey();
                minValue = entry.getValue();
            }
        }

        // Keep track of nodes that have been visited to avoid infinite loops
        Set<InetSocketAddress> visitedNodes = new HashSet<>();
        visitedNodes.add(new InetSocketAddress(TCP_ADDRESS, tcpPort));

        // Search for the min value in connected nodes
        for (InetSocketAddress connection : connections) {
            if (!visitedNodes.contains(connection)) {
                visitedNodes.add(connection);
                try {
                    Socket socket = new Socket(connection.getAddress(), connection.getPort());
                    PrintWriter os = new PrintWriter(socket.getOutputStream());
                    os.println("get-min " + uuid);
                    os.flush();
                    InputStream is = socket.getInputStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(is));
                    String response = br.readLine();

                    if (response != null) {
                        String[] parts = response.split(":");
                        int key = Integer.parseInt(parts[0]);
                        int value = Integer.parseInt(parts[1]);
                        if (value < minValue) {
                            minKey = key;
                            minValue = value;
                        }
                    }
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        out.println(minKey + ":" + minValue);
        out.close();
    }

    private void handleNewRecordCommand(int key, int value, PrintWriter out) {
        data.remove(key);
        data.put(key, value);
        out.println("OK");
    }

}

