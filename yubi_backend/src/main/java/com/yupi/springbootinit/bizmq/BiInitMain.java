package com.yupi.springbootinit.bizmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * 用于创建测试程序用到的交换机和队列（只用在程序启动前执行一次）
 */

public class BiInitMain {

    public static void main(String[] args) {
        try {
            // 创建连接工厂
            ConnectionFactory factory = new ConnectionFactory();
            //设置连接地址
            factory.setHost("localhost");
            //获取一个连接
            Connection connection = factory.newConnection();
            //获取一个连接频道
            Channel channel = connection.createChannel();
            //说明交换机类型为“direct”
            channel.exchangeDeclare(BiMqConstant.BI_EXCHANGE_NAME, "direct");
            //声明队列
            channel.queueDeclare(BiMqConstant.BI_QUEUE_NAME, true, false, false, null);
            //绑定交换机
            channel.queueBind(BiMqConstant.BI_QUEUE_NAME, BiMqConstant.BI_EXCHANGE_NAME, "bi_routingKey");
        } catch (Exception e) {
            // 异常处理
        }
    }
}
