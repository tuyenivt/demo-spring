package com.example.rabbitmq.consumer;

import com.example.rabbitmq.config.RabbitMQConfig;
import com.example.rabbitmq.dto.RpcRequest;
import com.example.rabbitmq.dto.RpcResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RpcConsumer {

    @RabbitListener(id = "rpc", queues = RabbitMQConfig.RPC_QUEUE)
    public RpcResponse handleRpcRequest(RpcRequest request) {
        log.info("Received RPC request: {}", request);
        var response = new RpcResponse(request.getId(), "Reply to: " + request.getMessage());
        log.info("Sending RPC response: {}", response);
        return response;
    }
}
