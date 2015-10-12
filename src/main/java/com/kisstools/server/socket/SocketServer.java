/**
 * @author dawson dong
 */

package com.kisstools.server.socket;

import com.kisstools.server.packet.Packet;
import com.kisstools.thread.KissExecutor;
import com.kisstools.utils.CloseUtil;
import com.kisstools.utils.DeviceUtil;
import com.kisstools.utils.LogUtil;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;

public class SocketServer {

    public static final String TAG = "SocketServer";

    public static final int PORT = 8964;

    private Thread listenerThread;

    private ServerSocket portListener;

    private Executor executor;

    private Set<Socket> openConnections;

    public SocketServer() {
        executor = KissExecutor.createExecutor(10, Thread.NORM_PRIORITY);
        openConnections = new HashSet<Socket>();
    }

    private void registerClient(Socket socket) {
        openConnections.add(socket);
    }

    private void unregisterClient(Socket socket) {
        openConnections.remove(socket);
    }

    public synchronized void closeAllConnections() {
        for (Socket socket : openConnections) {
            CloseUtil.close(socket);
        }
    }

    class SocketHandler implements Runnable {

        private boolean connected;

        private Socket socket;

        private InetAddress address;

        private int port;

        public SocketHandler(Socket socket) {
            this.connected = true;
            this.socket = socket;
            this.address = socket.getInetAddress();
            this.port = socket.getPort();
        }

        public void run() {
            registerClient(socket);
            OutputStream outputStream = null;
            InputStream inputStream = null;

            try {
                inputStream = socket.getInputStream();
                InputStreamReader isr = new InputStreamReader(inputStream);
                BufferedReader bf = new BufferedReader(isr);

                outputStream = socket.getOutputStream();
                BufferedOutputStream bos = new BufferedOutputStream(
                        outputStream);
                DataOutputStream dos = new DataOutputStream(bos);
                while (connected) {
                    String content = bf.readLine();
                    if (content == null || !connected) {
                        break;
                    }

                    LogUtil.d(TAG, "receive request " + content);
                    Packet request = Packet.unpack(content);
                    Packet response = processPacket(request);

                    // just send back the request
                    if (response == null) {
                        response = request;
                    }

                    LogUtil.d(TAG, "sending response " + response);
                    byte[] bytes = Packet.pack(response).getBytes();
                    dos.write(bytes);
                    dos.flush();
                    LogUtil.d(TAG, "response sent");
                }
            } catch (Exception e) {
                e.printStackTrace();
                LogUtil.e(TAG, "socket exception!");
                connected = false;
            } finally {
                CloseUtil.close(inputStream);
                CloseUtil.close(outputStream);
                CloseUtil.close(socket);
                unregisterClient(socket);
            }
            LogUtil.e(TAG, "finish socket " + address + ":" + port);
        }

    }

    class ServerThread extends Thread {

        public void run() {
            try {
                boolean running = true;
                while (running) {
                    Socket socket = portListener.accept();
                    InetAddress address = socket.getInetAddress();
                    int port = socket.getPort();
                    LogUtil.d(TAG, "connection from " + address + ":" + port
                            + " established");
                    SocketHandler handler = new SocketHandler(socket);
                    executor.execute(handler);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void start() {
        try {
            portListener = new ServerSocket(PORT);
            LogUtil.e(TAG, "server socket at port " + PORT + " succeed!");
        } catch (Exception e) {
            LogUtil.e(TAG, "server socket at port " + PORT + " failed!");
            e.printStackTrace();
        }
        listenerThread = new ServerThread();
        listenerThread.setName("SocketServer");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void stop() {
        CloseUtil.close(portListener);
        closeAllConnections();
    }

    private Packet processPacket(Packet request) {
        if (request == null || request.getCommand() == Packet.CMD_NONE) {
            return null;
        }

        Packet response = new Packet();
        String command = request.getCommand();
        if (Packet.CMD_SYS_INFO.equals(command)) {
            response.setData(DeviceUtil.getBuildInfo());
        }

        return response;
    }

}
