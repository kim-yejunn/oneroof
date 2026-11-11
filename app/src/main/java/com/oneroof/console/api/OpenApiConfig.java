package com.oneroof.console.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

	@Bean
	OpenAPI oneroofOpenApi() {
		return new OpenAPI().info(new Info().title("OneRoof API")
			.version("1")
			.description("""
					관련 거래처·판매장려금 지급 통제 (AX 인재전쟁 삼일PwC, 업무 W07).

					- 기준일은 2026-07-01 입니다. 모든 날짜 계산은 이 날짜를 기준으로 합니다.
					- 처리 API 는 결과를 바로 돌려주지 않습니다. 모듈 사이 단계가 커밋 직후 비동기로 이어지므로, 조회 API 로 결과를 확인합니다.
					- 역할 전환과 은행 회신은 시연을 위한 모의 처리입니다. 원본 데이터는 수정하지 않습니다.
					"""));
	}
}
