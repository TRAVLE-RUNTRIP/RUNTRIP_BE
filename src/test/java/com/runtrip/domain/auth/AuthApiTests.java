package com.runtrip.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 쿠키 속성은 실제 서버 응답에만 실리므로 MockMvc 대신 실제 포트로 호출한다
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthApiTests {

	static final String PASSWORD = "test-password";

	@LocalServerPort
	int port;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	JsonMapper jsonMapper;

	final HttpClient client = HttpClient.newHttpClient();
	String email;

	@BeforeEach
	void createUser() {
		email = "auth-" + UUID.randomUUID() + "@runtrip.test";
		jdbcTemplate.update("insert into users (email, password_hash, nickname) values (?, ?, ?)",
				email, passwordEncoder.encode(PASSWORD), "인증테스트");
	}

	@AfterEach
	void deleteUser() {
		jdbcTemplate.update("delete from users where email = ?", email);
	}

	@Test
	void 올바른_계정으로_로그인하면_쿠키와_사용자_정보() throws Exception {
		HttpResponse<String> response = login(email, PASSWORD);

		assertThat(response.statusCode()).isEqualTo(200);
		String setCookie = response.headers().firstValue("Set-Cookie").orElseThrow();
		assertThat(setCookie).startsWith("JSESSIONID=")
				.contains("HttpOnly", "Secure", "SameSite=Lax", "Max-Age=86400")
				.doesNotContain("Domain=");

		JsonNode body = jsonMapper.readTree(response.body());
		assertThat(body.get("success").asBoolean()).isTrue();
		assertThat(body.at("/data/userId").isNumber()).isTrue();
		assertThat(body.at("/data/email").asString()).isEqualTo(email);
		assertThat(body.at("/data/nickname").asString()).isEqualTo("인증테스트");
	}

	@Test
	void 틀린_비밀번호는_INVALID_CREDENTIALS() throws Exception {
		assertInvalidCredentials(login(email, "wrong-password"));
	}

	@Test
	void 없는_이메일은_INVALID_CREDENTIALS() throws Exception {
		assertInvalidCredentials(login("none-" + email, PASSWORD));
	}

	@Test
	void 쿠키로_프로필_조회() throws Exception {
		String cookie = login(email, PASSWORD).headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];

		HttpResponse<String> response = client.send(
				HttpRequest.newBuilder(uri("/api/users/me")).header("Cookie", cookie).GET().build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		JsonNode body = jsonMapper.readTree(response.body());
		assertThat(body.at("/data/email").asString()).isEqualTo(email);
		assertThat(body.at("/data/nickname").asString()).isEqualTo("인증테스트");
	}

	@Test
	void 쿠키_없이_호출하면_UNAUTHENTICATED() throws Exception {
		HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/api/users/me")).GET().build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(401);
		JsonNode body = jsonMapper.readTree(response.body());
		assertThat(body.get("success").asBoolean()).isFalse();
		assertThat(body.at("/error/code").asString()).isEqualTo("UNAUTHENTICATED");
	}

	private void assertInvalidCredentials(HttpResponse<String> response) {
		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
		assertThat(jsonMapper.readTree(response.body()).at("/error/code").asString()).isEqualTo("INVALID_CREDENTIALS");
	}

	private HttpResponse<String> login(String email, String password) throws Exception {
		String json = jsonMapper.writeValueAsString(new LoginBody(email, password));
		return client.send(HttpRequest.newBuilder(uri("/api/auth/login"))
						.header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString(json))
						.build(),
				HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	record LoginBody(String email, String password) {
	}
}
