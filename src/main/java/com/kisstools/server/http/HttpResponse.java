package com.kisstools.server.http;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by dawson on 10/11/15.
 */

public class HttpResponse {

    public HttpStatus status;

    public Map<String, String> header;

    public InputStream body;

    public HttpResponse() {
        header = new HashMap<String, String>();
    }

    public void setBody(String bodyText) {
        byte[] bytes;
        try {
            bytes = bodyText.getBytes("UTF-8");
        } catch (Throwable t) {
            bytes = new byte[0];
        }
        this.header.put("Content-Length", "" + bytes.length);
        this.body = new ByteArrayInputStream(bytes);
    }
}