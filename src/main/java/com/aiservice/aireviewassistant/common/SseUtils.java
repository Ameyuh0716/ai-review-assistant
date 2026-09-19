package com.aiservice.aireviewassistant.common;

/**
 * Server-Sent Events（SSE）帧处理工具。
 * <p>
 * SSE 规范要求客户端在解析 {@code data:} 行时剥离其后的一个前导空格
 * （参见 <a href="https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation">WHATWG 规范</a>）。
 * Spring 的编码器把含换行的文本拆成多个 {@code data:} 行且不追加空格，
 * 若某行内容本身以空格开头（markdown 缩进、硬换行、列表续行），该空格会被浏览器吃掉。
 * 本工具为每一行预置一个空格，经浏览器剥离后与原始文本逐字节一致。
 * </p>
 */
public final class SseUtils {

    private SseUtils() {
    }

    /**
     * 为 SSE 数据的每一行预置一个前导空格。
     *
     * @param frame 原始 SSE 帧内容
     * @return 每行前均带一个空格的帧内容
     */
    public static String padSseLines(String frame) {
        if (frame == null || frame.isEmpty()) {
            return frame;
        }
        StringBuilder sb = new StringBuilder(frame.length() + 8);
        sb.append(' ');
        for (int i = 0; i < frame.length(); i++) {
            char c = frame.charAt(i);
            sb.append(c);
            if (c == '\n' && i < frame.length() - 1) {
                // 换行后补位（行尾换行无需再补，由 Spring 生成的行本身处理）
                sb.append(' ');
            }
        }
        return sb.toString();
    }
}
