package com.runtrip.domain.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.runtrip.domain.course.service.CourseCleanupJob;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// API 명세 7~10번과 미저장 코스 48시간 정리
@SpringBootTest
@AutoConfigureMockMvc
class CourseSavedApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	CourseCleanupJob courseCleanupJob;

	Long userId;
	Long otherUserId;
	List<String> placeIds;

	@BeforeEach
	void setUp() {
		userId = insertUser();
		otherUserId = insertUser();
		placeIds = jdbcTemplate.queryForList(
				"select place_id from places where category <> '기타' order by place_id limit 2", String.class);
	}

	@AfterEach
	void tearDown() {
		jdbcTemplate.update("delete from runs where user_id in (?, ?)", userId, otherUserId);
		jdbcTemplate.update("delete from courses where user_id in (?, ?)", userId, otherUserId);
		jdbcTemplate.update("delete from users where user_id in (?, ?)", userId, otherUserId);
	}

	@Test
	void 저장하면_saved_at을_기록하고_다시_불러도_처음_시각() throws Exception {
		Long courseId = insertCourse(userId, null, OffsetDateTime.now());

		String first = mockMvc.perform(as(userId, put("/api/courses/{id}/saved", courseId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.courseId").value(courseId))
				.andExpect(jsonPath("$.data.saved").value(true))
				.andExpect(jsonPath("$.data.savedAt").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
		OffsetDateTime savedAt = savedAt(courseId);
		assertThat(savedAt).isNotNull();

		mockMvc.perform(as(userId, put("/api/courses/{id}/saved", courseId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.savedAt").value(com.jayway.jsonpath.JsonPath.<String>read(first, "$.data.savedAt")));
		assertThat(savedAt(courseId)).isEqualTo(savedAt);
	}

	@Test
	void 목록은_저장한_내_코스만_저장_시각_최신순() throws Exception {
		OffsetDateTime now = OffsetDateTime.now();
		Long older = insertCourse(userId, now.minusDays(2), now.minusDays(3));
		Long newer = insertCourse(userId, now.minusHours(1), now.minusDays(3));
		insertCourse(userId, null, now);
		insertCourse(otherUserId, now, now);

		mockMvc.perform(as(userId, get("/api/courses/saved")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.count").value(2))
				.andExpect(jsonPath("$.data.courses[0].courseId").value(newer))
				.andExpect(jsonPath("$.data.courses[1].courseId").value(older))
				.andExpect(jsonPath("$.data.courses[0].name").value("테스트 코스"))
				.andExpect(jsonPath("$.data.courses[0].type").value("ONE_WAY"))
				.andExpect(jsonPath("$.data.courses[0].distanceKm").value(5.12))
				.andExpect(jsonPath("$.data.courses[0].region").value("서울 중구"))
				.andExpect(jsonPath("$.data.courses[0].preferences[1]").value("NATURE"))
				.andExpect(jsonPath("$.data.courses[0].savedAt").isNotEmpty());
	}

	@Test
	void 저장한_코스가_없으면_빈_목록() throws Exception {
		mockMvc.perform(as(userId, get("/api/courses/saved")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.count").value(0))
				.andExpect(jsonPath("$.data.courses").isEmpty());
	}

	@Test
	void 상세는_생성_응답과_같은_형식() throws Exception {
		Long courseId = insertCourse(userId, OffsetDateTime.now(), OffsetDateTime.now());
		jdbcTemplate.update("update course_places set reason = '야경이 좋아요', reason_source = 'GEMINI' "
				+ "where course_id = ? and visit_order = 1", courseId);

		mockMvc.perform(as(userId, get("/api/courses/{id}", courseId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.courseId").value(courseId))
				.andExpect(jsonPath("$.data.type").value("ONE_WAY"))
				.andExpect(jsonPath("$.data.distanceKm").value(5.12))
				.andExpect(jsonPath("$.data.estimatedMinutes").value(31))
				.andExpect(jsonPath("$.data.start.lat").value(37.5658))
				.andExpect(jsonPath("$.data.start.lng").value(126.9751))
				.andExpect(jsonPath("$.data.end.lat").value(37.5796))
				.andExpect(jsonPath("$.data.end.lng").value(126.977))
				.andExpect(jsonPath("$.data.region").value("서울 중구"))
				.andExpect(jsonPath("$.data.preferences[0]").value("NIGHT_VIEW"))
				.andExpect(jsonPath("$.data.path.length()").value(3))
				.andExpect(jsonPath("$.data.path[0][0]").value(37.5658))
				.andExpect(jsonPath("$.data.path[0][1]").value(126.9751))
				.andExpect(jsonPath("$.data.places.length()").value(2))
				.andExpect(jsonPath("$.data.places[0].placeId").value(placeIds.get(0)))
				.andExpect(jsonPath("$.data.places[0].order").value(1))
				.andExpect(jsonPath("$.data.places[0].routeLat").value(37.57))
				.andExpect(jsonPath("$.data.places[0].routeLng").value(126.976))
				.andExpect(jsonPath("$.data.places[0].reason").value("야경이 좋아요"))
				.andExpect(jsonPath("$.data.places[1].placeId").value(placeIds.get(1)))
				.andExpect(jsonPath("$.data.places[1].order").value(2))
				.andExpect(jsonPath("$.data.places[1].reason").isEmpty())
				.andExpect(jsonPath("$.data.safety").isEmpty())
				.andExpect(jsonPath("$.data.saved").value(true))
				.andExpect(jsonPath("$.data.savedAt").isNotEmpty());
	}

	@Test
	void 저장하지_않은_내_코스도_상세_조회() throws Exception {
		Long courseId = insertCourse(userId, null, OffsetDateTime.now());

		mockMvc.perform(as(userId, get("/api/courses/{id}", courseId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.saved").value(false))
				.andExpect(jsonPath("$.data.savedAt").isEmpty());
	}

	@Test
	void 저장_취소하면_saved_at이_NULL이고_다시_불러도_204() throws Exception {
		Long courseId = insertCourse(userId, OffsetDateTime.now(), OffsetDateTime.now());

		mockMvc.perform(as(userId, delete("/api/courses/{id}/saved", courseId)))
				.andExpect(status().isNoContent());
		assertThat(savedAt(courseId)).isNull();

		mockMvc.perform(as(userId, delete("/api/courses/{id}/saved", courseId)))
				.andExpect(status().isNoContent());
		mockMvc.perform(as(userId, get("/api/courses/saved")))
				.andExpect(jsonPath("$.data.count").value(0));
	}

	@Test
	void 다른_계정의_코스는_COURSE_NOT_FOUND() throws Exception {
		Long courseId = insertCourse(otherUserId, OffsetDateTime.now(), OffsetDateTime.now());

		assertNotFound(put("/api/courses/{id}/saved", courseId));
		assertNotFound(get("/api/courses/{id}", courseId));
		assertNotFound(delete("/api/courses/{id}/saved", courseId));
		assertThat(savedAt(courseId)).isNotNull();
	}

	@Test
	void 없는_코스는_COURSE_NOT_FOUND() throws Exception {
		long missing = Long.MAX_VALUE;

		assertNotFound(put("/api/courses/{id}/saved", missing));
		assertNotFound(get("/api/courses/{id}", missing));
		assertNotFound(delete("/api/courses/{id}/saved", missing));
	}

	@Test
	void 로그인하지_않으면_UNAUTHENTICATED() throws Exception {
		mockMvc.perform(get("/api/courses/saved"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
		mockMvc.perform(get("/api/courses/1"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 만든_지_48시간_지난_임시_코스만_정리한다() throws Exception {
		OffsetDateTime now = OffsetDateTime.now();
		Long expired = insertCourse(userId, null, now.minusHours(49));
		Long recent = insertCourse(userId, null, now.minusHours(47));
		Long savedOld = insertCourse(userId, now.minusHours(48), now.minusHours(49));
		UUID runId = UUID.randomUUID();
		jdbcTemplate.update("""
				insert into runs (run_id, user_id, course_id, type, title, started_at, ended_at, distance_km,
				                  duration_sec, avg_pace_sec, segments)
				values (?, ?, ?, 'COURSE', '테스트 코스', now(), now(), 5.0, 1800, 360, '[]'::jsonb)""",
				runId, userId, expired);

		courseCleanupJob.deleteExpiredUnsavedCourses();

		assertThat(courseIds()).containsExactlyInAnyOrder(recent, savedOld);
		assertThat(jdbcTemplate.queryForObject("select count(*) from course_places where course_id = ?",
				Integer.class, expired)).isZero();
		// 러닝 기록은 남고 코스 연결만 끊긴다
		assertThat(jdbcTemplate.queryForObject("select course_id from runs where run_id = ?", Long.class, runId))
				.isNull();
	}

	private void assertNotFound(MockHttpServletRequestBuilder request) throws Exception {
		mockMvc.perform(as(userId, request))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("COURSE_NOT_FOUND"));
	}

	private MockHttpServletRequestBuilder as(Long user, MockHttpServletRequestBuilder request) {
		return request.with(authentication(UsernamePasswordAuthenticationToken.authenticated(user, null, List.of())));
	}

	private Long insertUser() {
		return jdbcTemplate.queryForObject(
				"insert into users (email, password_hash, nickname) values (?, ?, ?) returning user_id",
				Long.class, "saved-" + UUID.randomUUID() + "@runtrip.test", "hash", "저장테스트");
	}

	// 서울시청 → 경복궁 편도, 장소 2곳
	private Long insertCourse(Long owner, OffsetDateTime savedAt, OffsetDateTime createdAt) {
		Long courseId = jdbcTemplate.queryForObject("""
				insert into courses (user_id, name, type, distance_km, start_point, end_point, region, preferences, path,
				                     saved_at, created_at)
				values (?, '테스트 코스', 'ONE_WAY', 5.12,
				        ST_SetSRID(ST_MakePoint(126.9751, 37.5658), 4326), ST_SetSRID(ST_MakePoint(126.977, 37.5796), 4326),
				        '서울 중구', 'NIGHT_VIEW,NATURE',
				        ST_SetSRID(ST_GeomFromText('LINESTRING(126.9751 37.5658, 126.976 37.57, 126.977 37.5796)'), 4326),
				        ?, ?)
				returning course_id""", Long.class, owner, savedAt, createdAt);
		for (int i = 0; i < placeIds.size(); i++) {
			jdbcTemplate.update("""
					insert into course_places (course_id, place_id, visit_order, route_point)
					values (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326))""",
					courseId, placeIds.get(i), i + 1, 126.976 + i * 0.001, 37.57 + i * 0.001);
		}
		return courseId;
	}

	private OffsetDateTime savedAt(Long courseId) {
		return jdbcTemplate.queryForObject("select saved_at from courses where course_id = ?", OffsetDateTime.class,
				courseId);
	}

	private List<Long> courseIds() {
		return jdbcTemplate.queryForList("select course_id from courses where user_id = ?", Long.class, userId);
	}
}
