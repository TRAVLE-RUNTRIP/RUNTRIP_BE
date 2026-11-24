package com.runtrip.domain.course.controller;

import com.runtrip.domain.course.dto.CourseCreateRequest;
import com.runtrip.domain.course.dto.CourseResponse;
import com.runtrip.domain.course.service.CourseService;
import com.runtrip.global.config.ApiErrorExamples;
import com.runtrip.global.rsdata.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Course", description = "코스 생성")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/courses")
public class CourseController {

	private final CourseService courseService;

	@Operation(summary = "코스 생성", description = "순환 코스를 만들어 임시 저장합니다. 지금은 도착지 지정을 지원하지 않습니다.")
	@ApiResponse(responseCode = "201", description = "생성 성공")
	@ApiResponse(responseCode = "400", description = "입력값 오류",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.TARGET_KM_RANGE)))
	@ApiResponse(responseCode = "401", description = "세션 쿠키가 없거나 만료됨",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.UNAUTHENTICATED)))
	@ApiResponse(responseCode = "422", description = "갈 만한 장소나 조합이 없음",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.NO_PLACES)))
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RsData<CourseResponse> create(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@Valid @RequestBody CourseCreateRequest request) {
		return RsData.success(courseService.createLoop(userId, request));
	}
}
