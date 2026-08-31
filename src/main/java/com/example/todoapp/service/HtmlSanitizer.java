package com.example.todoapp.service;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Tiptap 본문 HTML을 저장 전에 정화한다 (CLAUDE.md 절대 규칙 8). 허용 태그는 Phase 6 Tiptap 최종 설정(heading
 * 2~3, bold/italic, list, blockquote, code, link — strike/horizontalRule/underline 제외)과 일치시켜야
 * 한다.
 */
@Component
public class HtmlSanitizer {

    private static final Safelist SAFELIST =
            Safelist.none()
                    .addTags(
                            "p",
                            "h2",
                            "h3",
                            "strong",
                            "em",
                            "ul",
                            "ol",
                            "li",
                            "blockquote",
                            "pre",
                            "code",
                            "br",
                            "a")
                    .addAttributes("a", "href")
                    .addProtocols("a", "href", "http", "https", "mailto")
                    .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer");

    public String sanitize(String html) {
        if (html == null) {
            return null;
        }
        return Jsoup.clean(html, SAFELIST);
    }
}
