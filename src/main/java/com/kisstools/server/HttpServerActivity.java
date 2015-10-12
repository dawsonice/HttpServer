package com.kisstools.server;

import android.os.Bundle;
import android.widget.TextView;

import com.kisstools.framework.base.BaseActivity;
import com.kisstools.utils.SystemUtil;

/**
 * Created by dawson on 10/11/15.
 */
public class HttpServerActivity extends BaseActivity {

    public static final String TAG = "HttpServerActivity";

    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_http_server);

        TextView tvTips = (TextView) findViewById(R.id.tv_tips);
        String ipAddress = SystemUtil.getIPAddress();
        int port = 7777;
        String tips = "请使用浏览器打开\nhttp://" + ipAddress + ":" + port + "/file";
        tvTips.setText(tips);
    }

}
