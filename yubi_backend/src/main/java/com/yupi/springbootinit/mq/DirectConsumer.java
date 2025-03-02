package com.yupi.springbootinit.mq;

import com.rabbitmq.client.*;

public class DirectConsumer {
  // 定义我们正在监听的交换机名称
  private static final String EXCHANGE_NAME = "direct-exchange";

  public static void main(String[] argv) throws Exception {
    // 创建连接工厂
    ConnectionFactory factory = new ConnectionFactory();
    // 设置连接工厂的主机地址为本地主机
    factory.setHost("localhost");
    // 建立与 RabbitMQ 服务器的连接
    Connection connection = factory.newConnection();
    // 创建一个通道
    Channel channel = connection.createChannel();
	// 声明一个 direct 类型的交换机
    channel.exchangeDeclare(EXCHANGE_NAME, "direct");
      // 创建队列1，随机分配一个队列名称
      String queueName1 = "xiaoyu_queue";
      channel.queueDeclare(queueName1, true, false, false, null);
      //队列1绑定交换机，并且绑定键“xiaoyu”
      channel.queueBind(queueName1, EXCHANGE_NAME, "xiaoyu");

      // 创建队列2
      String queueName2 = "xiaopi_queue";
      channel.queueDeclare(queueName2, true, false, false, null);
      //队列2绑定交换机,并且绑定键“xiaopi”
      channel.queueBind(queueName2, EXCHANGE_NAME, "xiaopi");
      // 打印等待消息的提示信息
      System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

    // 创建一个 DeliverCallback 实例来处理接收到的消息
    DeliverCallback xiaoyudeliverCallback = (consumerTag, delivery) -> {
        String message = new String(delivery.getBody(), "UTF-8");
        System.out.println(" [xiaoyu] Received '" +
                delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
    };

    DeliverCallback xiaopideliverCallback = (consumerTag, delivery) -> {
        String message = new String(delivery.getBody(), "UTF-8");
        System.out.println(" [xiaopi] Received '" +
                delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
    };
    // 开始消费队列中的消息，设置自动确认消息已被消费
    channel.basicConsume(queueName1, true, xiaoyudeliverCallback, consumerTag -> { });
    channel.basicConsume(queueName2, true, xiaopideliverCallback, consumerTag -> { });
  }
}
