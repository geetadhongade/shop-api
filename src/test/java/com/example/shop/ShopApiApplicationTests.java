package com.example.shop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Publisher off: this test uses the real database, and must never send its events to Kafka
@SpringBootTest(properties = "shop.outbox.publisher.enabled=false")
class ShopApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
