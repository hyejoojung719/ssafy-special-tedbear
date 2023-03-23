package com.ssafy.tedbear.global.common.oauth2;

import java.io.IOException;
import java.time.LocalDateTime;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import com.ssafy.tedbear.domain.member.entity.Member;
import com.ssafy.tedbear.domain.member.entity.MemberLevel;
import com.ssafy.tedbear.domain.member.entity.MemberScore;
import com.ssafy.tedbear.domain.member.repository.MemberRepository;
import com.ssafy.tedbear.global.common.oauth2.jwt.JwtProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

	private final MemberRepository memberRepository;
	private final MemberLevelRepository memberLevelRepository;
	private final MemberScoreRepository memberScoreRepository;
	private final JwtProvider jwtProvider;

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
		Authentication authentication) throws IOException, ServletException {

		String accessToken = jwtProvider.createAccessToken(authentication);
		String refreshToken = jwtProvider.createRefreshToken(authentication);

		CustomOAuth2User oAuth2User = (CustomOAuth2User)authentication.getPrincipal();
		saveOrUpdateUser(refreshToken, oAuth2User);

		ResponseCookie cookie = ResponseCookie.from("refresh", refreshToken)
			.httpOnly(true)
			.maxAge(JwtProvider.REFRESH_TOKEN_VALIDATE_TIME)
			.path("/")
			.build();

		clearAuthenticationAttributes(request, response);

		response.addHeader("Set-Cookie", cookie.toString());
		// response.getWriter().write(accessToken);
		response.addHeader("Authorization", "Bearer " + accessToken);
	}

	private void saveOrUpdateUser(String refreshToken, CustomOAuth2User oAuth2User) {
		MemberLevel memberLevel = MemberLevel.builder().levelExp(1).createdDate(LocalDateTime.now()).build();
		MemberScore memberScore = MemberScore.builder().score(null).createdDate(LocalDateTime.now()).build();
		Member member = memberRepository.findByUid(oAuth2User.getUid())
			.map(entity -> entity.updateRefreshToken(refreshToken))
			.orElse(oAuth2User.toEntity(oAuth2User.getNickname(), memberLevel, memberScore, null));

		memberScoreRepository.save(memberScore);
		memberLevelRepository.save(memberLevel);
		memberRepository.save(member);
	}

	protected void clearAuthenticationAttributes(HttpServletRequest request, HttpServletResponse response) {
		super.clearAuthenticationAttributes(request); // WebAttributes.AUTHENTICATION_EXCEPTION 변수에 정의된 세션 값 지우기
	}
}
