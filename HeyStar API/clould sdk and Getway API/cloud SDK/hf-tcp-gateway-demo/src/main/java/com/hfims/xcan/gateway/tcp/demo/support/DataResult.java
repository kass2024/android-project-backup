package com.hfims.xcan.gateway.tcp.demo.support;

import com.hfims.xcan.gateway.netty.error.CgiErrorEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 数据结果对象
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class DataResult<T> extends BaseResult implements Serializable {
    private T data;

    public DataResult(CgiErrorEnum errorCode) {
        this(errorCode, null);
    }

    public DataResult(CgiErrorEnum errorCode, T data) {
        super(errorCode.getCode(), errorCode.getMsg());
        this.data = data;
    }

    public DataResult(String code, String msg) {
        this(code, msg, null);
    }

    public DataResult(String code, String msg, T data) {
        super(code, msg);
        this.data = data;
    }
}
