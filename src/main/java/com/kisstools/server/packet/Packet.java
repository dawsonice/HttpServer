/**
 * @author dawson dong
 */

package com.kisstools.server.packet;

import com.alibaba.fastjson.JSONObject;
import com.kisstools.utils.JSONUtil;

public class Packet {

    public static final String COMMAND = "command";

    public static final String CMD_NONE = null;

    public static final String CMD_SYS_INFO = "sysinfo";

    public static final String DATA = "data";

    public static final String PARAMS = "params";

    public static Packet unpack(String text) {
        Packet packet = new Packet();
        if (text == null || text.isEmpty()) {
            return packet;
        }
        JSONObject jo = JSONUtil.parseObject(text);
        if (jo == null) {
            packet.data = text;
            return packet;
        }

        packet.command = JSONUtil.getString(jo, COMMAND);
        packet.data = JSONUtil.getString(jo, DATA);
        packet.params = JSONUtil.getJSONObject(jo, PARAMS);

        return packet;
    }

    public static String pack(Packet packet) {
        if (packet == null) {
            return null;
        }

        JSONObject jo = new JSONObject();
        jo.put(COMMAND, packet.command);
        jo.put(DATA, packet.data);
        jo.put(PARAMS, packet.params);
        return jo.toJSONString();
    }

    private String data;

    private String command;

    private JSONObject params;

    public Packet() {
        this.command = CMD_NONE;
    }

    public String toString() {
        String paramsText = params == null ? "" : params.toJSONString();
        return "[command] " + command + " [data] " + data + " [params] "
                + paramsText;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public JSONObject getParams() {
        return params;
    }

    public void setParams(JSONObject params) {
        this.params = params;
    }
}
