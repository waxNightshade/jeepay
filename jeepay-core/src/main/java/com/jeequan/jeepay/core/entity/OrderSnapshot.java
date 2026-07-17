package com.jeequan.jeepay.core.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("t_order_snapshot")
public class OrderSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderId;

    private Byte orderType;

    private String mchRespData;

    private Date mchRespTime;
}
