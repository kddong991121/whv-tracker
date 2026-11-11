package com.whv.tracker.eligibility;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 포스트코드 → 지역명 (참고용 표시). 인정 판정에는 쓰지 않는다 (판정은 PostcodeRegistry).
 * 출처: ABS SAL 2021 - POA 2021 (data.gov.au, CC BY 2.5 AU). docs/sources/build_localities.py로 만든 CSV를
 * 기동 시 한 번 읽어 메모리에 둔다. 외부 API는 호출하지 않는다.
 */
@Component
public class PostcodeLocalities {

    static final String SEED = "seed/postcode_localities.csv";
    /** 화면에 이름을 몇 개까지 보여줄지. 나머지는 "외 N곳". */
    static final int SHOWN = 3;

    /**
     * @param main  대표 지역명 (예: Toowoomba)
     * @param state 주 약자 (예: QLD)
     * @param names 동네 이름들 (대표 지역명이 들어간 동네 먼저)
     */
    public record Locality(String postcode, String main, String state, List<String> names) {

        /** 화면용 앞쪽 몇 개. */
        public List<String> shown() {
            return names.subList(0, Math.min(SHOWN, names.size()));
        }

        /** 화면에 안 보여준 나머지 개수. */
        public int more() {
            return Math.max(0, names.size() - SHOWN);
        }

        /** 한 줄 요약: "Toowoomba City, North Toowoomba, South Toowoomba 외 30곳". */
        public String summary() {
            return String.join(", ", shown()) + (more() > 0 ? " 외 " + more() + "곳" : "");
        }
    }

    private final Map<String, Locality> byPostcode = new HashMap<>();

    public PostcodeLocalities() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                new ClassPathResource(SEED).getInputStream(), StandardCharsets.UTF_8))) {
            in.readLine(); // 헤더
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> f = PostcodeRegistry.splitCsv(line);
                byPostcode.put(f.get(0), new Locality(f.get(0), f.get(2), f.get(1),
                        List.copyOf(Arrays.asList(f.get(3).split("\\|")))));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("지역명 CSV를 읽지 못했습니다: " + SEED, e);
        }
    }

    /** 숫자 4자리 포스트코드의 지역명. 형식이 틀리거나 자료에 없으면 비어 있다. */
    public Optional<Locality> find(String postcode) {
        if (postcode == null || !postcode.trim().matches("\\d{4}")) {
            return Optional.empty();
        }
        return Optional.ofNullable(byPostcode.get(postcode.trim()));
    }
}
