package com.whv.tracker.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 공유 미리보기(og:url, og:image)에 쓰는 사이트 주소 (PRD 4.10c). 끝의 / 는 뗀다. */
@ControllerAdvice
public class SiteAdvice {

    private final String siteUrl;

    public SiteAdvice(@Value("${whv.site-url:https://whv88.duckdns.org}") String siteUrl) {
        String v = siteUrl == null ? "" : siteUrl.trim();
        this.siteUrl = v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }

    @ModelAttribute("siteUrl")
    public String siteUrl() {
        return siteUrl;
    }
}
