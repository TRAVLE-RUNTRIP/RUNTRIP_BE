package com.runtrip.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.runtrip.global.error.ApiException;
import com.runtrip.global.error.ErrorCode;
import com.runtrip.global.rsdata.RsData;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(EnvironmentSetupTests.TestConfig.class)
class EnvironmentSetupTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JsonMapper jsonMapper;

	@Autowired
	EntityManager entityManager;

	@Test
	@WithMockUser
	void 성공_응답은_RsData_형식() throws Exception {
		mockMvc.perform(get("/test/success"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").value("ok"))
				.andExpect(jsonPath("$.error").value(nullValue()))
				.andExpect(jsonPath("$.timestamp").value(endsWith("+09:00")));
	}

	@Test
	@WithMockUser
	void ApiException은_RsData_실패_형식() throws Exception {
		mockMvc.perform(get("/test/api-exception"))
				.andExpect(status().is(422))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.data").value(nullValue()))
				.andExpect(jsonPath("$.error.code").value("NO_PLACES"))
				.andExpect(jsonPath("$.error.message").value(ErrorCode.NO_PLACES.getMessage()))
				.andExpect(jsonPath("$.timestamp").value(endsWith("+09:00")));
	}

	@Test
	@WithMockUser
	void 입력값_오류는_VALIDATION_ERROR() throws Exception {
		mockMvc.perform(post("/test/validation").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.message").value("이름을 입력해 주세요."));
	}

	@Test
	@WithMockUser
	void 잘못된_JSON은_VALIDATION_ERROR() throws Exception {
		mockMvc.perform(post("/test/validation").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
	}

	@Test
	@WithMockUser
	void 없는_경로는_RESOURCE_NOT_FOUND() throws Exception {
		mockMvc.perform(get("/test/none"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	@WithMockUser
	void 지원하지_않는_메서드는_METHOD_NOT_ALLOWED() throws Exception {
		mockMvc.perform(post("/test/success").with(csrf()))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
	}

	@Test
	@WithMockUser
	void 예상못한_예외는_SERVER_ERROR() throws Exception {
		mockMvc.perform(get("/test/unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error.code").value("SERVER_ERROR"));
	}

	@Test
	void JSON_시간은_한국시간_ISO8601() {
		OffsetDateTime utc = OffsetDateTime.of(2026, 11, 5, 12, 30, 0, 0, ZoneOffset.UTC);
		assertThat(jsonMapper.writeValueAsString(utc)).isEqualTo("\"2026-11-05T21:30:00+09:00\"");
	}

	@Test
	@Transactional
	void PostGIS_Point가_JTS로_왕복_매핑() {
		Point seoul = new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(126.9751, 37.5658));

		Point result = entityManager.unwrap(Session.class)
				.createNativeQuery("select cast(:point as geometry) as g", Point.class)
				.addScalar("g", Point.class)
				.setParameter("point", seoul)
				.getSingleResult();

		assertThat(result.getX()).isEqualTo(126.9751);
		assertThat(result.getY()).isEqualTo(37.5658);
		assertThat(result.getSRID()).isEqualTo(4326);
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		TestController testController() {
			return new TestController();
		}
	}

	@RestController
	static class TestController {

		@GetMapping("/test/success")
		RsData<String> success() {
			return RsData.success("ok");
		}

		@GetMapping("/test/api-exception")
		void apiException() {
			throw new ApiException(ErrorCode.NO_PLACES);
		}

		@GetMapping("/test/unexpected")
		void unexpected() {
			throw new IllegalStateException("boom");
		}

		@PostMapping("/test/validation")
		void validation(@Valid @RequestBody TestRequest request) {
		}
	}

	record TestRequest(@NotBlank(message = "이름을 입력해 주세요.") String name) {
	}
}
