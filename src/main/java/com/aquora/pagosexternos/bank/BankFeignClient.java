package com.aquora.pagosexternos.bank;

import com.aquora.pagosexternos.bank.client.BankApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * El plugin genero BankApi.java como una interfaz MVC normal (sin @FeignClient:
 * paso esperado con generatorName=spring, pero no lo agrego esta vez). En vez de
 * tocar el codigo generado (no se debe editar, se regenera en cada build), se
 * envuelve aqui con @FeignClient. Como spring-cloud-starter-openfeign usa por
 * defecto SpringMvcContract, entiende las mismas anotaciones @RequestMapping /
 * @RequestHeader / @RequestBody que trae BankApi, así que con solo extenderla
 * ya queda funcionando como cliente Feign real.
 */
@FeignClient(name = "bankApi", url = "${app.bank.base-url}")
public interface BankFeignClient extends BankApi {
}
