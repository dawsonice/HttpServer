package com.kisstools.server.http;

import android.text.TextUtils;

import com.kisstools.thread.Background;
import com.kisstools.utils.CloseUtil;
import com.kisstools.utils.LogUtil;
import com.kisstools.utils.StringUtil;
import com.kisstools.utils.UrlUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Created by dawson on 10/9/15.
 */
public class HttpServer {

    public static final String TAG = "HttpServer";

    private static final int HTTP_PORT = 7777;

    private static final int SOCKET_TIMEOUT = 10000;

    private int httpPort = HTTP_PORT;

    private ServerSocket serverSocket;

    private int socketTimeout = SOCKET_TIMEOUT;

    private Thread serverThread;

    private Set<Socket> connections;

    private Map<String, RequestHandler> handlers;

    public HttpServer() {
        this(HTTP_PORT);
    }

    public HttpServer(int port) {
        this.httpPort = port;
        handlers = new HashMap<String, RequestHandler>();
        connections = new HashSet<Socket>();
        addHandler("/file", new FileHandler());
    }

    public void setHttpPort(int port) {
        this.httpPort = port;
    }

    public int getHttpPort() {
        return this.httpPort;
    }

    private void addConnection(Socket socket) {
        connections.add(socket);
    }

    private void removeConnection(Socket socket) {
        connections.remove(socket);
    }

    class SocketHandler implements Runnable {

        private Socket socket;

        private InetAddress address;

        private int port;

        public SocketHandler(Socket socket) {
            this.socket = socket;
            this.address = socket.getInetAddress();
            this.port = socket.getPort();
        }

        public void run() {
            addConnection(socket);
            OutputStream outputStream = null;
            InputStream inputStream = null;

            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();

                HttpRequest request = parseRequest(inputStream);
                if (request != null) {
                    HttpResponse response = serveRequest(request);
                    sendResponse(response, outputStream);
                }
            } catch (Throwable t) {
                LogUtil.e(TAG, "connection exception!", t);
            } finally {
                CloseUtil.close(inputStream);
                CloseUtil.close(outputStream);
                CloseUtil.close(socket);
                removeConnection(socket);
            }
            LogUtil.d(TAG, "disconnect " + address + ":" + port);
        }
    }

    public void removeHandler(String path) {
        if (TextUtils.isEmpty(path)) {
            return;
        }
        handlers.remove(path);
    }

    public void addHandler(String path, RequestHandler handler) {
        if (TextUtils.isEmpty(path) || handler == null) {
            return;
        }
        handlers.put(path, handler);
    }

    private HttpResponse serveRequest(HttpRequest request) {
        LogUtil.d(TAG, "serveRequest " + request.path);
        HttpResponse response = new HttpResponse();
        RequestHandler handler = handlers.get(request.path);
        try {
            if (handler != null && handler.handleRequest(request, response)) {
                return response;
            }
        } catch (Throwable t) {
            response.status = HttpStatus.INTERNAL_ERROR;
            response.setBody(StringUtil.stringify(t));
            return response;
        }
        // default handler
        response.status = HttpStatus.NOT_FOUND;
        response.setBody(HttpStatus.NOT_FOUND.getDescription());
        return response;
    }

    private void sendResponse(HttpResponse response, OutputStream outputStream) throws Exception {
        LogUtil.d(TAG, "sendResponse " + response.status.getStatusCode());
        OutputStreamWriter osw = new OutputStreamWriter(outputStream, "UTF-8");
        BufferedWriter bw = new BufferedWriter(osw);
        PrintWriter pw = new PrintWriter(bw, false);
        HttpStatus ss = response.status;
        String statusLine = ss.getStatusCode() + " " + ss.getDescription();
        pw.print("HTTP/1.1 " + statusLine + " \r\n");
        LogUtil.d(TAG, statusLine);
        if (response.header != null) {
            for (String key : response.header.keySet()) {
                String value = response.header.get(key);
                String line = key + ": " + value;
                pw.print(line + "\r\n");
                LogUtil.d(TAG, "response header " + line);
            }
        }
        pw.print("Connection: close\r\n");
        pw.print("\r\n");
        pw.flush();
        sendBody(outputStream, response.body);
        outputStream.flush();
    }

    private void sendBody(OutputStream outputStream, InputStream body) throws IOException {
        LogUtil.d(TAG, "sendBody");
        if (body == null) {
            LogUtil.d(TAG, "empty response body");
            return;
        }

        int BUFFER_SIZE = 16 * 1024;
        byte[] buff = new byte[BUFFER_SIZE];
        while (true) {
            int read = body.read(buff, 0, BUFFER_SIZE);
            if (read <= 0) {
                break;
            }
            outputStream.write(buff, 0, read);
        }
    }

    private HttpRequest parseRequest(InputStream inputStream) throws IOException {
        LogUtil.d(TAG, "parseRequest");
        InputStreamReader isr = new InputStreamReader(inputStream);
        BufferedReader br = new BufferedReader(isr);
        String line = br.readLine();
        if (line == null) {
            return null;
        }
        LogUtil.d(TAG, "protocol line " + line);
        StringTokenizer st = new StringTokenizer(line);

        HttpRequest request = new HttpRequest();
        request.method = st.nextToken();
        String path = st.nextToken();
        parseQuery(request, path);
        request.path = UrlUtil.getPath(path);
        request.protocol = st.nextToken();

        line = br.readLine();
        while (line != null && line.trim().length() > 0) {
            int p = line.indexOf(':');
            if (p >= 0) {
                String key = line.substring(0, p).trim().toLowerCase(Locale.US);
                String value = line.substring(p + 1).trim();
                LogUtil.d(TAG, "request header " + line);
                request.header.put(key, value);
            }
            line = br.readLine();
        }

        return request;
    }

    private void parseQuery(HttpRequest request, String path) {
        int index = path.indexOf("?");
        if (index <= 0) {
            return;
        }
        String params = path.substring(index + 1);
        StringTokenizer st = new StringTokenizer(params, "&");
        while (st.hasMoreTokens()) {
            String pair = st.nextToken();
            int sep = pair.indexOf('=');
            String key = "";
            String value = "";
            if (sep >= 0) {
                key = decodeParam(pair.substring(0, sep)).trim();
                value = decodeParam(pair.substring(sep + 1));
            } else {
                key = decodeParam(pair).trim();
            }
            request.query.put(key, value);
            LogUtil.d(TAG, "request query " + key + " " + value);
        }
    }

    protected static String decodeParam(String param) {
        String decoded = null;
        try {
            decoded = URLDecoder.decode(param, "UTF8");
        } catch (Throwable ignored) {
            LogUtil.e(TAG, "decode exception", ignored);
        }
        return decoded;
    }


    class ServerThread extends Thread {

        public void run() {
            try {
                boolean running = true;
                while (running) {
                    Socket socket = serverSocket.accept();
                    InetAddress address = socket.getInetAddress();
                    int port = socket.getPort();
                    LogUtil.d(TAG, "connection from " + address + ":" + port + " established");
                    SocketHandler handler = new SocketHandler(socket);
                    Background.execute(handler);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public boolean start() {
        LogUtil.d(TAG, "start http server");
        try {
            this.serverSocket = new ServerSocket(httpPort);
            serverSocket.setReuseAddress(true);
            serverThread = new ServerThread();
            serverThread.setName("HttpServer");
            serverThread.setDaemon(true);
            serverThread.start();
            LogUtil.d(TAG, "http server serve port " + httpPort);
        } catch (Throwable t) {
            LogUtil.e(TAG, "start server exception.", t);
            return false;
        }
        return true;
    }

    public void stop() {
        CloseUtil.close(serverSocket);
        closeAllConnections();
    }

    public synchronized void closeAllConnections() {
        for (Socket socket : connections) {
            CloseUtil.close(socket);
        }
    }

}