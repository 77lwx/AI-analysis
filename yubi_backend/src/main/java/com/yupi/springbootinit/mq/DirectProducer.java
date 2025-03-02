package com.yupi.springbootinit.mq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.util.Scanner;

import static io.lettuce.core.pubsub.PubSubOutput.Type.message;

public class DirectProducer {

  // 定义交换机名称为"direct-exchange"
  private static final String EXCHANGE_NAME = "direct-exchange";

  public static void main(String[] argv) throws Exception {
    // 创建连接工厂
    ConnectionFactory factory = new ConnectionFactory();
    // 设置连接工厂的主机地址为本地主机
    factory.setHost("localhost");
    // 建立连接并创建通道
    try (Connection connection = factory.newConnection();
         Channel channel = connection.createChannel()) {
        // 使用通道声明交换机，类型为direct
        channel.exchangeDeclare(EXCHANGE_NAME, "direct");
    	// 获取严重程度（路由键）和消息内容
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNext()) {
            // 从用户输入中获取消息内容
            String userInput = scanner.nextLine();
            //空格分隔
            String[] splits = userInput.split(" ");
            //分为消息和key
            String message=splits[0];
            String routingKey=splits[1];

            // 发布消息到直连交换机
            // 使用通道的basicPublish方法将消息发布到交换机
            // EXCHANGE_NAME表示要发布消息的交换机的名称
            // routingKey表示消息的路由键，用于确定消息被路由到哪个队列
            // null表示不使用额外的消息属性
            // message.getBytes("UTF-8")将消息内容转换为UTF-8编码的字节数组
            channel.basicPublish(EXCHANGE_NAME, routingKey, null, message.getBytes("UTF-8"));
            // 打印发送的消息内容
            System.out.println(" [x] Sent '" + message + " with routing:" + routingKey + "'");
        }
    	// 发布消息到交换机
    }
  }
}
