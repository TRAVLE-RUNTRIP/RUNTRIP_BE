package com.runtrip.domain.course.controller;

import com.runtrip.domain.course.dto.CourseCreateRequest;
import com.runtrip.domain.course.dto.CourseResponse;
import com.runtrip.domain.course.dto.CourseSaveResponse;
import com.runtrip.domain.course.dto.MinDistanceRequest;
import com.runtrip.domain.course.dto.MinDistanceResponse;
import com.runtrip.domain.course.dto.SavedCourseListResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

	@Operation(summary = "코스 생성·다시 만들기",
			description = "end가 null이면 순환, 있으면 편도 코스를 만들어 임시 저장합니다. "
					+ "다시 만들기는 이미 보여준 장소 조합을 excludedCombos로 보냅니다. "
					+ "편도 코스 이름은 start.name, end.name으로 만들고 없으면 첫 장소·마지막 장소 이름을 씁니다.")
	@ApiResponse(responseCode = "201", description = "생성 성공")
	@ApiResponse(responseCode = "400", description = "입력값 오류",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.TARGET_KM_RANGE)))
	@ApiResponse(responseCode = "401", description = "세션 쿠키가 없거나 만료됨",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.UNAUTHENTICATED)))
	@ApiResponse(responseCode = "422",
			description = "출발지·도착지가 서울 밖, 목표 거리가 최소 입력 거리보다 짧음, 갈 만한 장소·조합이 없음, 다시 만들 코스가 없음",
			content = @Content(examples = {
					@ExampleObject(name = "OUT_OF_SEOUL", value = ApiErrorExamples.OUT_OF_SEOUL),
					@ExampleObject(name = "DISTANCE_TOO_SHORT", value = ApiErrorExamples.DISTANCE_TOO_SHORT),
					@ExampleObject(name = "NO_PLACES", value = ApiErrorExamples.NO_PLACES),
					@ExampleObject(name = "NO_MORE_COURSES", value = ApiErrorExamples.NO_MORE_COURSES)}))
	@ApiResponse(responseCode = "500", description = "AI 서버·OSRM 실패 또는 시간 초과",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.SERVER_ERROR)))
	@ApiResponse(responseCode = "504", description = "코스 생성 10초 초과",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.GENERATION_TIMEOUT)))
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RsData<CourseResponse> create(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@Valid @RequestBody CourseCreateRequest request) {
		return RsData.success(courseService.create(userId, request));
	}

	@Operation(summary = "최소 입력 거리 확인",
			description = "도착지를 지정했을 때 입력할 수 있는 최소 목표 거리를 반환합니다. 순환 코스는 호출하지 않습니다.")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	@ApiResponse(responseCode = "400", description = "입력값 오류",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.DESTINATION_REQUIRED)))
	@ApiResponse(responseCode = "401", description = "세션 쿠키가 없거나 만료됨",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.UNAUTHENTICATED)))
	@ApiResponse(responseCode = "422", description = "출발지·도착지가 서울 밖이거나 최소 입력 거리가 21km 초과",
			content = @Content(examples = {
					@ExampleObject(name = "OUT_OF_SEOUL", value = ApiErrorExamples.OUT_OF_SEOUL),
					@ExampleObject(name = "DESTINATION_TOO_FAR", value = ApiErrorExamples.DESTINATION_TOO_FAR)}))
	@PostMapping("/min-distance")
	public RsData<MinDistanceResponse> minDistance(@Valid @RequestBody MinDistanceRequest request) {
		return RsData.success(courseService.minDistance(request));
	}

	@Operation(summary = "코스 저장", description = "임시 코스를 저장합니다. 여러 번 호출해도 처음 저장 시각을 그대로 줍니다.")
	@ApiResponse(responseCode = "200", description = "저장 성공")
	@ApiResponse(responseCode = "401", description = "세션 쿠키가 없거나 만료됨",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.UNAUTHENTICATED)))
	@ApiResponse(responseCode = "404", description = "없는 코스이거나 다른 계정의 코스",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.COURSE_NOT_FOUND)))
	@PutMapping("/{courseId}/saved")
	public RsData<CourseSaveResponse> save(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long courseId) {
		return RsData.success(courseService.save(userId, courseId));
	}

	@Operation(summary = "저장 코스 목록", description = "저장한 코스를 저장 시각 최신순으로 반환합니다.")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	@ApiResponse(responseCode = "401", description = "세션 쿠키가 없거나 만료됨",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.UNAUTHENTICATED)))
	@GetMapping("/saved")
	public RsData<SavedCourseListResponse> savedCourses(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		return RsData.success(courseService.savedCourses(userId));
	}

	@Operation(summary = "코스 상세",
			description = "코스 생성 응답과 같은 형식입니다. 저장하지 않은 내 코스도 조회할 수 있습니다. 안전 정보는 아직 null입니다.")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	@ApiResponse(responseCode = "401", description = "세션 쿠키가 없거나 만료됨",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.UNAUTHENTICATED)))
	@ApiResponse(responseCode = "404", description = "없는 코스이거나 다른 계정의 코스",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.COURSE_NOT_FOUND)))
	@GetMapping("/{courseId}")
	public RsData<CourseResponse> detail(@Parameter(hidden = true) @AuthenticationPrincipal Long userId,
			@PathVariable Long courseId) {
		return RsData.success(courseService.detail(userId, courseId));
	}

	@Operation(summary = "저장 코스 삭제", description = "저장을 취소합니다. 이미 취소한 내 코스도 204입니다. 러닝 기록은 영향을 받지 않습니다.")
	@ApiResponse(responseCode = "204", description = "삭제 성공")
	@ApiResponse(responseCode = "401", description = "세션 쿠키가 없거나 만료됨",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.UNAUTHENTICATED)))
	@ApiResponse(responseCode = "404", description = "없는 코스이거나 다른 계정의 코스",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.COURSE_NOT_FOUND)))
	@DeleteMapping("/{courseId}/saved")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unsave(@Parameter(hidden = true) @AuthenticationPrincipal Long userId, @PathVariable Long courseId) {
		courseService.unsave(userId, courseId);
	}
}
