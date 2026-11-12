package com.runtrip.domain.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.runtrip.domain.location.service.SeoulRegionResolver;
import com.runtrip.global.error.ApiException;
import com.runtrip.global.error.ErrorCode;
import com.runtrip.global.tmap.TmapClient;
import com.runtrip.global.tmap.TmapClient.Address;
import com.runtrip.global.tmap.TmapClient.Poi;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LocationApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	SeoulRegionResolver seoulRegionResolver;

	@MockitoBean
	TmapClient tmapClient;

	@Test
	@WithMockUser
	void 검색_결과는_서울_안_장소만() throws Exception {
		given(tmapClient.searchPois("광장")).willReturn(List.of(
				poi("서울광장", "37.5663", "126.9779", "서울", "중구", "태평로1가", "31", "0", "세종대로", "110", "0"),
				poi("청계광장", "37.5692", "126.9776", "서울", "종로구", "서린동", "14", "0", "", "", ""),
				poi("수원 광장", "37.2636", "127.0286", "경기", "수원시 팔달구", "인계동", "1", "0", "효원로", "241", "0")));

		mockMvc.perform(get("/api/locations/search").param("query", " 광장 "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.results.length()").value(2))
				.andExpect(jsonPath("$.data.results[0].name").value("서울광장"))
				.andExpect(jsonPath("$.data.results[0].address").value("서울 중구 세종대로 110"))
				.andExpect(jsonPath("$.data.results[0].lat").value(37.5663))
				.andExpect(jsonPath("$.data.results[0].lng").value(126.9779))
				.andExpect(jsonPath("$.data.results[1].address").value("서울 종로구 서린동 14"));
	}

	@Test
	@WithMockUser
	void 부번이_있으면_본번_부번() throws Exception {
		given(tmapClient.searchPois("타워")).willReturn(List.of(
				poi("타워", "37.55", "126.98", "서울", "용산구", "용산동2가", "1", "3", "남산공원길", "105", "2")));

		mockMvc.perform(get("/api/locations/search").param("query", "타워"))
				.andExpect(jsonPath("$.data.results[0].address").value("서울 용산구 남산공원길 105-2"));
	}

	@Test
	@WithMockUser
	void 검색어가_없으면_VALIDATION_ERROR() throws Exception {
		mockMvc.perform(get("/api/locations/search"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.message").value("검색어를 입력해 주세요."));
	}

	@Test
	@WithMockUser
	void 검색어가_공백이면_VALIDATION_ERROR() throws Exception {
		mockMvc.perform(get("/api/locations/search").param("query", "  "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.error.message").value("검색어를 입력해 주세요."));
	}

	@Test
	void 로그인하지_않으면_UNAUTHENTICATED() throws Exception {
		mockMvc.perform(get("/api/locations/search").param("query", "경복궁"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
	}

	@Test
	void 서울_좌표는_구까지_지역_표시() {
		given(tmapClient.reverseGeocode(37.5663, 126.9779)).willReturn(Optional.of(new Address("서울특별시", "중구")));

		assertThat(seoulRegionResolver.requireSeoulRegion(37.5663, 126.9779)).isEqualTo("서울 중구");
	}

	@Test
	void 서울_밖_좌표는_OUT_OF_SEOUL() {
		given(tmapClient.reverseGeocode(37.2636, 127.0286)).willReturn(Optional.of(new Address("경기도", "수원시 팔달구")));

		assertOutOfSeoul(37.2636, 127.0286);
	}

	@Test
	void 주소가_없는_좌표는_OUT_OF_SEOUL() {
		given(tmapClient.reverseGeocode(37.0, 124.0)).willReturn(Optional.empty());

		assertOutOfSeoul(37.0, 124.0);
	}

	private void assertOutOfSeoul(double lat, double lng) {
		assertThatThrownBy(() -> seoulRegionResolver.requireSeoulRegion(lat, lng))
				.isInstanceOfSatisfying(ApiException.class,
						ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.OUT_OF_SEOUL));
	}

	private Poi poi(String name, String lat, String lon, String upper, String middle, String lower,
			String firstNo, String secondNo, String roadName, String firstBuildNo, String secondBuildNo) {
		return new Poi(name, lat, lon, upper, middle, lower, firstNo, secondNo, roadName, firstBuildNo, secondBuildNo);
	}
}
