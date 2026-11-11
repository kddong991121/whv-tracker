package com.whv.tracker.web;

import com.whv.tracker.eligibility.PostcodeLocalities;
import com.whv.tracker.eligibility.PostcodeRegistry;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 포스트코드 입력칸 아래 지역명 힌트용 (로그인 없이 공개, 읽기 전용).
 * 응답 예: {"postcode":"4350","main":"Toowoomba","state":"QLD","summary":"North Toowoomba, ... 외 30곳"}
 * ABS 지역명 자료에 없으면 주만 돌려준다 (main, summary 빈 값). 예: 4433은 내무부 목록에는 있지만
 * ABS 자료에 동네 이름이 없다. 주도 모르는 번호면 404. 입력값은 저장하지 않는다.
 */
@RestController
public class PostcodeApiController {

    private final PostcodeLocalities localities;
    private final PostcodeRegistry registry;

    public PostcodeApiController(PostcodeLocalities localities, PostcodeRegistry registry) {
        this.localities = localities;
        this.registry = registry;
    }

    @GetMapping("/api/postcode/{postcode}")
    public ResponseEntity<Map<String, String>> find(@PathVariable String postcode) {
        return localities.find(postcode)
                .map(l -> ResponseEntity.ok(Map.of("postcode", l.postcode(), "main", l.main(),
                        "state", l.state(), "summary", l.summary())))
                .orElseGet(() -> stateOnly(postcode));
    }

    private ResponseEntity<Map<String, String>> stateOnly(String postcode) {
        if (!postcode.matches("\\d{4}")) {
            return ResponseEntity.notFound().build();
        }
        var states = registry.statesFor(postcode);
        if (states.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("postcode", postcode, "main", "", "state", String.join("·", states),
                "summary", ""));
    }
}
