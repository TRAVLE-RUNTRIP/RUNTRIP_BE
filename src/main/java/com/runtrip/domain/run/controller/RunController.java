package com.runtrip.domain.run.controller;

import com.runtrip.domain.run.dto.RunCreateRequest;
import com.runtrip.domain.run.dto.RunCreateResponse;
import com.runtrip.domain.run.dto.RunListResponse;
import com.runtrip.domain.run.service.RunService;
import com.runtrip.domain.run.service.RunService.CreateResult;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Run", description = "러닝 기록")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/runs")
public class RunController {

	private final RunService runService;

	@Operation(summary = "러닝 기록 저장",
			description = "기록과 코스 저장을 한 번에 처리합니다. 코스 러닝은 그 코스를 저장하고, 자유 러닝은 경로로 새 코스를 만들어 저장합니다. "
					+ "임시 코스가 이미 정리됐으면 기록만 저장하고 courseSaved는 false입니다. "
					+ "같은 runId를 다시 보내면 저장하지 않고 200으로 같은 내용을 줍니다.")
	@ApiResponse(responseCode = "201", description = "저장 성공")
	@ApiResponse(responseCode = "200", description = "이미 저장한 runId")
	@ApiResponse(responseCode = "400", description = "입력값 오류",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.RUN_COURSE_ID)))
	@ApiResponse(responseCode = "401", description = "세션 쿠키가 없거나 만료됨",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.UNAUTHENTICATED)))
	@PostMapping
	public ResponseEntity<RsData<RunCreateResponse>> create(
			@Parameter(hidden = true) @AuthenticationPrincipal Long userId, @Valid @RequestBody RunCreateRequest request) {
		CreateResult result = runService.create(userId, request);
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(RsData.success(result.response()));
	}

	@Operation(summary = "러닝 기록 조회", description = "내 러닝 기록을 시작 시각 최신순으로 반환합니다.")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	@ApiResponse(responseCode = "401", description = "세션 쿠키가 없거나 만료됨",
			content = @Content(examples = @ExampleObject(value = ApiErrorExamples.UNAUTHENTICATED)))
	@GetMapping
	public RsData<RunListResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
		return RsData.success(runService.list(userId));
	}
}
