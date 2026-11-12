package com.runtrip.global.tmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

import com.runtrip.global.tmap.TmapClient.Address;
import com.runtrip.global.tmap.TmapClient.Poi;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TmapClientTests {

	MockRestServiceServer server;
	TmapClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new TmapClient(builder, "test-key");
	}

	@Test
	void POI_검색_응답을_읽는다() {
		server.expect(requestTo(startsWith("https://apis.openapi.sk.com/tmap/pois")))
				.andExpect(queryParam("searchKeyword", "%EA%B2%BD%EB%B3%B5%EA%B6%81"))
				.andExpect(header("appKey", "test-key"))
				.andRespond(withSuccess("""
						{"searchPoiInfo":{"totalCount":"1","count":"1","page":"1","pois":{"poi":[
						{"id":"1","name":"경복궁","frontLat":"37.57600","frontLon":"126.97700",
						"noorLat":"37.57960","noorLon":"126.97700","upperAddrName":"서울","middleAddrName":"종로구",
						"lowerAddrName":"세종로","firstNo":"1","secondNo":"0","roadName":"사직로",
						"firstBuildNo":"161","secondBuildNo":"0"}]}}}""", MediaType.APPLICATION_JSON));

		List<Poi> pois = client.searchPois("경복궁");

		assertThat(pois).singleElement().satisfies(poi -> {
			assertThat(poi.name()).isEqualTo("경복궁");
			assertThat(poi.frontLat()).isEqualTo("37.57600");
			assertThat(poi.upperAddrName()).isEqualTo("서울");
			assertThat(poi.roadName()).isEqualTo("사직로");
		});
		server.verify();
	}

	@Test
	void 검색_결과가_없으면_빈_목록() {
		server.expect(requestTo(startsWith("https://apis.openapi.sk.com/tmap/pois"))).andRespond(withNoContent());

		assertThat(client.searchPois("없는장소")).isEmpty();
	}

	@Test
	void 역지오코딩_응답을_읽는다() {
		server.expect(requestTo(startsWith("https://apis.openapi.sk.com/tmap/geo/reversegeocoding")))
				.andExpect(queryParam("lat", "37.5663"))
				.andExpect(queryParam("lon", "126.9779"))
				.andRespond(withSuccess("""
						{"addressInfo":{"fullAddress":"서울특별시 중구 태평로1가 31","addressType":"A10",
						"city_do":"서울특별시","gu_gun":"중구","eup_myun":"","legalDong":"태평로1가"}}""",
						MediaType.APPLICATION_JSON));

		assertThat(client.reverseGeocode(37.5663, 126.9779)).contains(new Address("서울특별시", "중구"));
	}

	@Test
	void 주소가_없는_좌표는_빈_값() {
		server.expect(requestTo(startsWith("https://apis.openapi.sk.com/tmap/geo/reversegeocoding")))
				.andRespond(withBadRequest());

		assertThat(client.reverseGeocode(37.0, 124.0)).isEmpty();
	}
}
