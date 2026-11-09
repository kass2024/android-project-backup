package com.qonics.utils;

import org.json.JSONObject;

public interface Callback {
    void HttpCallback(JSONObject object, int status_code, String status);
}
