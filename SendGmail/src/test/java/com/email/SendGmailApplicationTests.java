package com.email;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
		locations = "classpath:application-test.properties"
)
class SendGmailApplicationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private Environment environment;

	@Test
	void contextLoadsWithIsolatedTestConfiguration() {

		String databaseName = jdbcTemplate.queryForObject(
				"SELECT DATABASE()",
				String.class
		);

		assertEquals("bulkemailtest", databaseName);

		assertEquals(
				"false",
				environment.getRequiredProperty(
						"app.import.worker.enabled"
				)
		);

		assertEquals(
				"false",
				environment.getRequiredProperty(
						"app.email.worker.enabled"
				)
		);
	}
}