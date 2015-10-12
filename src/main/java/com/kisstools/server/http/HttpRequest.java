package com.kisstools.server.http;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by dawson on 10/11/15.
 */
public class HttpRequest {

    public String method;

    public String path;

    public String protocol;

    public Map<String, String> query;

    public Map<String, String> header;

    public HttpRequest() {
        header = new HashMap<>();
        query = new HashMap<>();
    }

}
