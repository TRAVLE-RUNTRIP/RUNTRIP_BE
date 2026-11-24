package com.runtrip.global.osrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.runtrip.global.osrm.OsrmClient.LatLng;
import com.runtrip.global.osrm.OsrmClient.Route;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OsrmClientTests {

	static final List<LatLng> POINTS = List.of(new LatLng(37.5658, 126.9751), new LatLng(37.5796, 126.977),
			new LatLng(37.5658, 126.9751));

	MockRestServiceServer server;
	OsrmClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new OsrmClient(builder, "http://osrm");
	}

	@Test
	void table은_경도_위도_순서로_요청하고_m를_반올림() {
		server.expect(requestTo(
						"http://osrm/table/v1/foot/126.9751,37.5658;126.977,37.5796;126.9751,37.5658?annotations=distance"))
				.andRespond(withSuccess("""
						{"code":"Ok","distances":[[0,1620.4,0],[1610.6,0,1610.6],[0,1620.4,0]]}""",
						MediaType.APPLICATION_JSON));

		assertThat(client.table(POINTS)).isDeepEqualTo(new int[][] {{0, 1620, 0}, {1611, 0, 1611}, {0, 1620, 0}});
		server.verify();
	}

	@Test
	void table에서_길이_없는_칸은_큰_값() {
		server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://osrm/table/v1/foot/")))
				.andRespond(withSuccess("""
						{"code":"Ok","distances":[[0,null],[null,0]]}""", MediaType.APPLICATION_JSON));

		assertThat(client.table(POINTS.subList(0, 2))).isDeepEqualTo(new int[][] {{0, 1_000_000}, {1_000_000, 0}});
	}

	@Test
	void route는_경로와_스냅_좌표를_위도_경도로_바꾼다() {
		server.expect(requestTo(
						"http://osrm/route/v1/foot/126.9751,37.5658;126.977,37.5796;126.9751,37.5658?overview=full&geometries=geojson"))
				.andRespond(withSuccess("""
						{"code":"Ok","routes":[{"distance":3240.7,"duration":2300,
						"geometry":{"type":"LineString","coordinates":[[126.9751,37.5658],[126.9771,37.5795],[126.9751,37.5658]]}}],
						"waypoints":[{"location":[126.9751,37.5658]},{"location":[126.9771,37.5795]},{"location":[126.9751,37.5658]}]}""",
						MediaType.APPLICATION_JSON));

		Route route = client.route(POINTS);

		assertThat(route.distanceM()).isEqualTo(3240.7);
		assertThat(route.path()).containsExactly(new LatLng(37.5658, 126.9751), new LatLng(37.5795, 126.9771),
				new LatLng(37.5658, 126.9751));
		assertThat(route.waypoints().get(1)).isEqualTo(new LatLng(37.5795, 126.9771));
		server.verify();
	}
}
