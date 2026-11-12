package com.runtrip.global.tmap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

// T map REST API 호출. 앱 키는 환경 변수 TMAP_APP_KEY
@Component
public class TmapClient {

	private static final String BASE_URL = "https://apis.openapi.sk.com";
	private static final int POI_COUNT = 20;

	private final RestClient restClient;

	@Autowired
	public TmapClient(@Value("${tmap.app-key}") String appKey) {
		this(RestClient.builder().requestFactory(requestFactory()), appKey);
	}

	// 테스트에서 MockRestServiceServer에 묶은 builder를 넘긴다
	public TmapClient(RestClient.Builder builder, String appKey) {
		this.restClient = builder.baseUrl(BASE_URL).defaultHeader("appKey", appKey).build();
	}

	public List<Poi> searchPois(String keyword) {
		PoiSearchResponse response = restClient.get()
				.uri(uri -> uri.path("/tmap/pois")
						.queryParam("version", 1)
						.queryParam("searchKeyword", keyword)
						.queryParam("count", POI_COUNT)
						.queryParam("reqCoordType", "WGS84GEO")
						.queryParam("resCoordType", "WGS84GEO")
						.build())
				.retrieve()
				.body(PoiSearchResponse.class);
		// 검색 결과가 없으면 T map은 204와 빈 본문을 준다
		if (response == null || response.searchPoiInfo() == null || response.searchPoiInfo().pois() == null) {
			return List.of();
		}
		return response.searchPoiInfo().pois().poi();
	}

	// 주소가 없는 좌표(바다, 국외 등)는 T map이 400을 주므로 빈 값으로 돌려준다
	public Optional<Address> reverseGeocode(double lat, double lng) {
		try {
			ReverseGeocodeResponse response = restClient.get()
					.uri(uri -> uri.path("/tmap/geo/reversegeocoding")
							.queryParam("version", 1)
							.queryParam("lat", lat)
							.queryParam("lon", lng)
							.queryParam("coordType", "WGS84GEO")
							.queryParam("addressType", "A10")
							.build())
					.retrieve()
					.body(ReverseGeocodeResponse.class);
			return Optional.ofNullable(response).map(ReverseGeocodeResponse::addressInfo);
		} catch (HttpClientErrorException.BadRequest ex) {
			return Optional.empty();
		}
	}

	private static SimpleClientHttpRequestFactory requestFactory() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(2));
		factory.setReadTimeout(Duration.ofSeconds(3));
		return factory;
	}

	// T map 응답의 숫자도 문자열로 온다
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Poi(
			String name,
			String frontLat,
			String frontLon,
			String upperAddrName,
			String middleAddrName,
			String lowerAddrName,
			String firstNo,
			String secondNo,
			String roadName,
			String firstBuildNo,
			String secondBuildNo) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Address(
			@JsonProperty("city_do") String cityDo,
			@JsonProperty("gu_gun") String guGun) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record PoiSearchResponse(SearchPoiInfo searchPoiInfo) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SearchPoiInfo(Pois pois) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Pois(List<Poi> poi) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ReverseGeocodeResponse(Address addressInfo) {
	}
}
