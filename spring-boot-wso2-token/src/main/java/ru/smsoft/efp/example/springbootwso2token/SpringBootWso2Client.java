package ru.smsoft.efp.example.springbootwso2token;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * Клиент-обертка для взаимодействия с ЕФП через WSO2 Api manager
 * Поддерживает перехват ошибок вызова и автоматическое обновление OAuth token'a
 * @author Александр Ревков
 */
@Service
public class SpringBootWso2Client {
    private static final Logger log = LoggerFactory.getLogger(SpringBootWso2Client.class);
    private final SpringBootWso2TokenConfiguration wso2TokenConfiguration;
    private volatile String accessToken;

    public SpringBootWso2Client(SpringBootWso2TokenConfiguration wso2TokenConfiguration) {
        this.wso2TokenConfiguration = wso2TokenConfiguration;
    }

    /**
     * Метод принудительного получения OAuth токена из WSO2 Api manager.
     * Использует параметры из конфигурационного файла.
     * @return {@code true} - токен успешно получен, {@code false} - не удалось получить токен
     */
    public boolean getToken() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new ManualErrorHandler());
        restTemplate.getInterceptors().add(new BasicAuthenticationInterceptor(wso2TokenConfiguration.getConsumerKey(), wso2TokenConfiguration.getConsumerSecret()));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "client_credentials");
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        ResponseEntity<String> responseEntity = restTemplate.postForEntity(wso2TokenConfiguration.getTokenUrl(), request, String.class);
        log.debug("Get token response HTTP {}: {}", responseEntity.getStatusCode(), responseEntity.getBody());
        boolean result = false;
        if (responseEntity.getStatusCode().equals(HttpStatus.OK)) {
            try {
                JsonNode json = new ObjectMapper().readTree(responseEntity.getBody());
                if (json.has("access_token")) {
                    this.accessToken = json.get("access_token").asText();
                    log.info("Get OAuth access_token: {}", this.accessToken);
                    result = true;
                } else {
                    log.warn("Response json doesn't contains access token");
                }
            } catch (Exception e) {
                log.error("Exception while processing response json");
            }
        } else {
            log.warn("Token service return bad status code");
        }

        return result;
    }

    /**
     * Метод принудительного отзыва OAuth токена из WSO2 Api manager.
     * Использует параметры из конфигурационного файла.
     * @return {@code true} - токен успешно отозван, {@code false} - не удалось отозвать токен
     */
    public boolean revokeToken() {
        boolean result = false;
        if (StringUtils.hasText(this.accessToken)) {
            log.debug("Try to revoke token: {}", this.accessToken);
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.setErrorHandler(new ManualErrorHandler());
            restTemplate.getInterceptors().add(new BasicAuthenticationInterceptor(wso2TokenConfiguration.getConsumerKey(), wso2TokenConfiguration.getConsumerSecret()));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("token", this.accessToken);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            ResponseEntity<String> responseEntity = restTemplate.postForEntity(wso2TokenConfiguration.getRevokeUrl(), request, String.class);
            log.debug("Revoke token response HTTP {}: {}", responseEntity.getStatusCode(), responseEntity.getBody());
            if (responseEntity.getStatusCode().equals(HttpStatus.OK)) {
                result = true;
                this.accessToken = null;
            } else {
                log.warn("Token service return bad status code");
            }
        } else {
            log.warn("AccessToken is null. Ignore revoke.");
        }

        return result;
    }
    /**
     * Пример метода вызова сервиса на ЕФП с использованием OAuth токена из WSO2 Api manager.
     * Использует параметры из конфигурационного файла.
     * Данный метод можно сделать generic, также можно добавить входные параметры типа url адреса и.т.д
     * @return Возвращает полученный ответ, в случае с ошибкой вернет @{null}
     */
    public ResponseEntity<String> callService() throws InterruptedException {
        boolean tokenResult = true;
        int initialCounter = 0;
        //Если токен не был ранее получен, то принудительно получаем его
        if (!StringUtils.hasText(this.accessToken)) {
            log.info("Initial accessToken is null. Try to fetch new one");
            do {
                tokenResult = this.getToken();
                initialCounter++;
                Thread.sleep(1000); //Опционально можно сделать задержку перед повторными вызовами
            } while (!tokenResult && initialCounter < wso2TokenConfiguration.getRetryCount());
        }
        if (!tokenResult) {
            log.error("Doesn't fetch token after {} times", wso2TokenConfiguration.getRetryCount());
            throw new IllegalStateException(String.format("Doesn't fetch token after %d times", wso2TokenConfiguration.getRetryCount()));
        }
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new ManualErrorHandler());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(this.accessToken);
        HttpEntity<String> request = new HttpEntity<>(headers);
        tokenResult = true;
        initialCounter = 0;
        ResponseEntity<String> responseEntity;
        do {
            log.info("Call service with token: {}", headers.getFirst("Authorization"));
            responseEntity = restTemplate.exchange(wso2TokenConfiguration.getServiceUrl(), HttpMethod.GET, request, String.class);
            log.info("Call service return HTTP {}: {}", responseEntity.getStatusCode(), responseEntity.getBody());
            if (responseEntity.getStatusCode().is2xxSuccessful()) {
                log.info("Successfull calling!");
                return responseEntity;
            } else if (responseEntity.getStatusCode().equals(HttpStatus.UNAUTHORIZED)) {
                log.warn("AccessToken maybe expired. Try to get new one.");
                tokenResult = this.getToken();
                if (tokenResult) {
                    //Устанавливаем новый токен в заголовок
                    headers.setBearerAuth(this.accessToken);
                }
            } else {
                log.error("Service error: {}", responseEntity.getStatusCode());
                //например на сервисные ошибки, можно не делать повторные вызовы при желании, нужно добавить тут break, либо добавить условие в цикл
                //сейчас обрабатываются повторно все ошибки
            }
            initialCounter++;
            Thread.sleep(1000); //Опционально можно сделать задержку перед повторными вызовами
        } while (!tokenResult && initialCounter < wso2TokenConfiguration.getRetryCount());

        return responseEntity;
    }
}
