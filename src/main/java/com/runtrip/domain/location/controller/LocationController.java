package com.runtrip.domain.location.controller;

import com.runtrip.domain.location.dto.LocationSearchResponse;
import com.runtrip.domain.location.service.LocationService;
import com.runtrip.global.config.ApiErrorExamples;
import com.runtrip.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Location", description = "출발지·도착지 검색")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/locations")
public class LocationController {

	private final LocationService locationService;

	@Operation(summary = "출발지·도착지 검색", description = "T map 장소 검색 결과 중 서울 안 장소만 반환합니다.")
	@ApiResponse(responseCode = "200", description = "검색 성공")
	@ApiResponse(responseCode = "400", description = "검색어가 비어 있음",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.QUERY_REQUIRED)))
	@ApiResponse(responseCode = "401", description = "세션 쿠키가 없거나 만료됨",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.UNAUTHENTICATED)))
	@GetMapping("/search")
	public RsData<LocationSearchResponse> search(
			@Parameter(description = "검색어", example = "경복궁")
			@RequestParam(required = false) @NotBlank(message = "검색어를 입력해 주세요.") String query) {
		return RsData.success(new LocationSearchResponse(locationService.search(query)));
	}
}
