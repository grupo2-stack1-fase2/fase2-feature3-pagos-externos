package com.aquora.pagosexternos.erp;

import com.aquora.pagosexternos.erp.client.ErpApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * Mismo patron que BankFeignClient: ErpApi se genera como interfaz MVC
 * (interfaceOnly=true, sin @FeignClient); se envuelve aqui para que
 * spring-cloud-starter-openfeign (SpringMvcContract) la trate como cliente
 * Feign real, sin tocar el codigo generado.
 */
@FeignClient(name = "erpApi", url = "${app.erp.base-url}", configuration = ErpFeignConfig.class)
public interface ErpFeignClient extends ErpApi {
}
