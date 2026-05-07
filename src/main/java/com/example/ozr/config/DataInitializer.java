package com.example.ozr.config;

import com.example.ozr.model.User;
import com.example.ozr.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            userRepository.save(User.builder()
                    .userId("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .name("관리자")
                    .department("IT팀")
                    .active(true)
                    .build());

            userRepository.save(User.builder()
                    .userId("user1")
                    .password(passwordEncoder.encode("user123"))
                    .name("홍길동")
                    .department("영업팀")
                    .active(true)
                    .build());

            log.info("초기 사용자 생성 완료 (admin, user1). 운영 환경에서는 반드시 비밀번호를 변경하세요.");
        }
    }
}
