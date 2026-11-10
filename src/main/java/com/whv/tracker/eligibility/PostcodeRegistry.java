package com.whv.tracker.eligibility;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 시드 CSV (원문 표 1~6, 2026-11-10 캡처)를 기동 시 한 번 읽어 메모리에 둔다. 외부 API는 호출하지 않는다.
 * CSV 원본은 docs/seed/에 있고, 여기서는 classpath 사본을 읽는다 (PRD 4.5).
 */
@Component
public class PostcodeRegistry {

    static final String SEED = "seed/specified_work_postcodes.csv";

    private final Map<String, List<PostcodeRule>> byPostcode = new HashMap<>();
    private final Map<String, List<PostcodeRule>> allByState = new HashMap<>();

    public PostcodeRegistry() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                new ClassPathResource(SEED).getInputStream(), StandardCharsets.UTF_8))) {
            in.readLine(); // 헤더
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> f = splitCsv(line);
                PostcodeRule rule = new PostcodeRule(f.get(0), f.get(1), f.get(2), f.get(3), f.get(4));
                Map<String, List<PostcodeRule>> target = "ALL".equals(rule.postcode()) ? allByState : byPostcode;
                String key = "ALL".equals(rule.postcode()) ? rule.state() : rule.postcode();
                target.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("시드 CSV를 읽지 못했습니다: " + SEED, e);
        }
    }

    /** 포스트코드에 직접 적힌 행 (ALL 행 제외). */
    public List<PostcodeRule> rulesFor(String postcode) {
        return byPostcode.getOrDefault(postcode, List.of());
    }

    /** 주 전체 (postcode = ALL) 행. */
    public List<PostcodeRule> allRulesFor(String state) {
        return allByState.getOrDefault(state, List.of());
    }

    /**
     * 포스트코드의 주 후보. 시드 CSV의 state를 우선하고 (결정 2026-11-10), CSV에 없으면 번호대 표를 쓴다 (PRD 4.3a).
     * CSV에 여러 주가 있으면 모두 돌려준다 (예: 0872 → NT, SA, WA).
     */
    public Set<String> statesFor(String postcode) {
        Set<String> states = new LinkedHashSet<>();
        for (PostcodeRule r : rulesFor(postcode)) {
            states.add(r.state());
        }
        if (states.isEmpty()) {
            String s = StateRanges.stateOf(Integer.parseInt(postcode));
            if (s != null) {
                states.add(s);
            }
        }
        return states;
    }

    /** 따옴표로 감싼 단순 CSV 한 줄을 나눈다 (필드 안의 쉼표 허용, "" 이스케이프 처리). */
    static List<String> splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
