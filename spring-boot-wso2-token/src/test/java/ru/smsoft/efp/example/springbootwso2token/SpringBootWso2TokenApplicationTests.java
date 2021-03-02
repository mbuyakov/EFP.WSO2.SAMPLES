package ru.smsoft.efp.example.springbootwso2token;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SpringBootWso2TokenApplicationTests {
	private static final Logger log = LoggerFactory.getLogger(SpringBootWso2TokenApplicationTests.class);
	@Autowired
	private SpringBootWso2Client springBootWso2Client;

	@Test
	@Order(1)
	void simpleCall() {
		try {
			ResponseEntity<String> result = springBootWso2Client.callService();
			Assertions.assertNotNull(result);
		} catch (InterruptedException e) {
			log.error("SimpleCall exception", e);
		}
	}

	@Test
	@Order(2)
	void revokeToken() {
		boolean result = springBootWso2Client.revokeToken();
		Assertions.assertTrue(result);
		result = springBootWso2Client.revokeToken();
		Assertions.assertFalse(result);
	}

	@Test
	@Order(3)
	void updateToken() {
		boolean result = springBootWso2Client.getToken();
		Assertions.assertTrue(result);
		try {
			ResponseEntity<String> response = springBootWso2Client.callService();
			Assertions.assertNotNull(response);
		} catch (InterruptedException e) {
			log.error("SimpleCall exception", e);
		}
		result = springBootWso2Client.revokeToken();
		Assertions.assertTrue(result);
	}

}
