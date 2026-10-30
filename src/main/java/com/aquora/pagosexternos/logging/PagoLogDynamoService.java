package com.aquora.pagosexternos.logging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j 
@Service 
@RequiredArgsConstructor 
public class PagoLogDynamoService {
    
    private final DynamoDbClient dynamoDbClient;

    @Value ("${app.aws.dynamodb.tabla-logs}")
    private String tablaLogs;

    public void registrarLog(Long pagoId, String destino, String resultado,
                              Integer httpStatus, String codigoError, String idExterno) {
        try {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("pagoId", AttributeValue.builder().n(pagoId.toString()).build());
            item.put("timestamp", AttributeValue.builder().s(Instant.now().toString()).build());
            item.put("destino", AttributeValue.builder().s(destino).build());
            item.put("resultado", AttributeValue.builder().s(resultado).build());
            if (httpStatus != null) item.put("httpStatus", AttributeValue.builder().n(httpStatus.toString()).build());
            if (codigoError != null) item.put("codigoError", AttributeValue.builder().s(codigoError).build());
            if (idExterno != null) item.put("idExterno", AttributeValue.builder().s(idExterno).build());

            dynamoDbClient.putItem(PutItemRequest.builder().tableName(tablaLogs).item(item).build());
        } catch (Exception e) {
            log.warn("No se pudo escribir el log en DynamoDB para pago {}: {}", pagoId, e.toString());
        }
    }
}
