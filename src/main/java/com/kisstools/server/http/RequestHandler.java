package com.kisstools.server.http;

/**
 * Created by dawson on 10/11/15.
 */
public interface RequestHandler {

    boolean handleRequest(HttpRequest request, HttpResponse response);

}
