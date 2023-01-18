import java.io.*;
import java.net.*;

public class DatabaseClient {

    public static void main(String[] args) {
        // Parse command line arguments
        InetSocketAddress gateway = null;
        String operation = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-gateway")) {
                String[] parts = args[++i].split(":");
                gateway = new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
            } else if (args[i].equals("-operation")) {
                if (args[i+1].equals("get-value") || args[i+1].equals("set-value") || args[i+1].equals("find-key") || args[i+1].equals("new-record")){
                    operation = args[i+1] + " ";
                    operation += args[i+2];
                }else operation = args[++i];
            }
        }
        if (gateway == null || operation == null) {
            System.err.println("ERROR: Missing -gateway or -operation argument");
            return;
        }
        // Connect to the gateway node
        Socket socket = new Socket();
        try {
            socket.connect(gateway);
        } catch (IOException e) {
            System.err.println("ERROR: Failed to connect to gateway node");
            return;
        }
        // Send the operation request
        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println(operation);
            String response = in.readLine();
            System.out.println(response);
            socket.close();

        } catch (IOException e) {
            System.err.println("ERROR: Failed to send operation request");
        }

    }
}