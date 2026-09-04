package com.example.todoapp.service;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Tiptap 본문 HTML을 저장 전에 정화한다 (CLAUDE.md 절대 규칙 8).
 *
 * <p>허용 태그는 Tiptap 에디터 설정(bold/italic, list, link, image —
 * strike/underline/horizontalRule/heading/blockquote/code/codeBlock 제외)과 일치시켜야 한다. 또한
 * 프론트엔드의 {@code src/lib/sanitize.ts} {@code ALLOWED_TAGS}와도 <b>정확히 같은 집합</b>이어야 한다.
 * 한쪽만 고치면 "넣었는데 저장하면 사라지는" 증상이 생긴다.
 *
 * <p><b>{@code img}에 {@code src}를 허용하지 않는다</b> (PRD F-49). 조회 URL은 30분 만료라 본문 HTML에
 * 박아두면 며칠 뒤 전부 깨진다. {@code data-attachment-id}만 저장하고 렌더 시점에 {@code src}를 주입한다.
 * 부수 효과로 {@code javascript:} src 주입 경로가 원천 차단되고, 본문 길이가 짧아져 {@code @Size(max =
 * 50000)} 제약에도 여유가 생긴다.
 */
@Component
public class HtmlSanitizer {

    private static final Safelist SAFELIST =
            Safelist.none()
                    .addTags("p", "strong", "em", "ul", "ol", "li", "br", "a", "img")
                    .addAttributes("a", "href")
                    .addProtocols("a", "href", "http", "https", "mailto")
                    .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer")
                    // src는 의도적으로 뺐다. 위 클래스 주석 참고.
                    .addAttributes("img", "alt", "data-attachment-id");

    public String sanitize(String html) {
        if (html == null) {
            return null;
        }
        return Jsoup.clean(html, SAFELIST);
    }
}
