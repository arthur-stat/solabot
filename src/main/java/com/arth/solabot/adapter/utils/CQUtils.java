package com.arth.solabot.adapter.utils;

import com.arth.solabot.core.bot.dto.ReplayedMessagePayloadDTO;
import com.arth.solabot.core.bot.dto.replay.AudioRef;
import com.arth.solabot.core.bot.dto.replay.ImageRef;
import com.arth.solabot.core.bot.dto.replay.MediaSourceType;
import com.arth.solabot.core.bot.dto.replay.VideoRef;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CQUtils {

    private CQUtils() {
    }

    // [CQ:image,...] / [CQ:record,...] / [CQ:video,...]
    // group(1)=type, group(2)= ",k=v,k=v"
    private static final Pattern CQ_TAG =
            Pattern.compile("\\[CQ:([a-zA-Z0-9_]+)((?:,[^\\]]+)?)\\]");

    /**
     * 把 CQ 串里的媒体段提取进 DTO，并把剩下纯文本放进 dto.addText(...)
     */
    public static void fillFromCQ(String cq, ReplayedMessagePayloadDTO dto) {
        if (cq == null) return;

        // 先解析媒体段
        Matcher m = CQ_TAG.matcher(cq);
        while (m.find()) {
            String type = m.group(1);
            String kvRaw = m.group(2);
            Map<String, String> kv = parseKV(kvRaw);

            switch (type) {
                case "image" -> dto.addImage(fromImageKV(kv));
                case "record" -> dto.addAudio(fromAudioKV(kv)); // 语音
                case "video" -> dto.addVideo(fromVideoKV(kv));
                default -> { /* 其他类型按需扩展，例如 face/at/reply 等 */ }
            }
        }

        // 去掉所有 CQ 段，做一次反转义，得到纯文本
        String textOnly = unescapeCQ(cq.replaceAll("\\[CQ:[^\\]]+\\]", ""));
        if (!textOnly.isBlank()) dto.addText(textOnly);
    }

    /* ---------------- internal helpers ---------------- */

    private static Map<String, String> parseKV(String kvRaw) {
        Map<String, String> m = new LinkedHashMap<>();
        if (kvRaw == null || kvRaw.isEmpty()) return m;
        // 去掉起始的逗号
        String s = kvRaw.charAt(0) == ',' ? kvRaw.substring(1) : kvRaw;
        for (String p : s.split(",")) {
            int i = p.indexOf('=');
            if (i > 0) {
                String k = p.substring(0, i);
                String v = p.substring(i + 1);
                m.put(k, unescapeCQ(v));
            }
        }
        return m;
    }

    // CQ 规范常见转义：& -> &amp; , , -> &#44; , [ -> &#91; , ] -> &#93; , 换行 -> &#10;
    private static String unescapeCQ(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.replace("&amp;", "&")
                .replace("&#44;", ",")
                .replace("&#91;", "[")
                .replace("&#93;", "]")
                .replace("&#10;", "\n");
    }

    private static ImageRef fromImageKV(Map<String, String> kv) {
        String url = kv.get("url");
        String file = kv.get("file");
        ImageRef ref = new ImageRef();
        if (notBlank(url)) {
            ref.setSourceType(MediaSourceType.URL);
            ref.setSource(url);
        } else {
            ref.setSourceType(MediaSourceType.FILE_ID);
            ref.setSource(file == null ? "" : file);
        }
        ref.setMime(kv.get("mime"));
        ref.setFilename(kv.get("filename"));
        if (kv.containsKey("width")) ref.setWidth(safeInt(kv.get("width")));
        if (kv.containsKey("height")) ref.setHeight(safeInt(kv.get("height")));
        ref.setMd5(kv.get("md5"));
        return ref;
    }

    private static AudioRef fromAudioKV(Map<String, String> kv) {
        String url = kv.get("url");
        String file = kv.get("file");
        AudioRef ref = new AudioRef();
        if (notBlank(url)) {
            ref.setSourceType(MediaSourceType.URL);
            ref.setSource(url);
        } else {
            ref.setSourceType(MediaSourceType.FILE_ID);
            ref.setSource(file == null ? "" : file);
        }
        ref.setMime(kv.get("mime"));
        ref.setFilename(kv.get("filename"));
        if (kv.containsKey("duration")) ref.setDurationSec(safeInt(kv.get("duration")));
        return ref;
    }

    private static VideoRef fromVideoKV(Map<String, String> kv) {
        String url = kv.get("url");
        String file = kv.get("file");
        VideoRef ref = new VideoRef();
        if (notBlank(url)) {
            ref.setSourceType(MediaSourceType.URL);
            ref.setSource(url);
        } else {
            ref.setSourceType(MediaSourceType.FILE_ID);
            ref.setSource(file == null ? "" : file);
        }
        ref.setMime(kv.get("mime"));
        ref.setFilename(kv.get("filename"));
        if (kv.containsKey("duration")) ref.setDurationSec(safeInt(kv.get("duration")));
        return ref;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static Integer safeInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return null;
        }
    }
}
