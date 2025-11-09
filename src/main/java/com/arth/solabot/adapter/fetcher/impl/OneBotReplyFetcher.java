package com.arth.solabot.adapter.fetcher.impl;

import com.arth.solabot.adapter.fetcher.ReplyFetcher;
import com.arth.solabot.adapter.sender.Sender;
import com.arth.solabot.adapter.utils.CQUtils;
import com.arth.solabot.core.bot.dto.ReplayedMessagePayloadDTO;
import com.arth.solabot.core.bot.dto.replay.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OneBotReplyFetcher implements ReplyFetcher {

    private final Sender sender;
    private final ObjectMapper objectMapper;

    public ReplayedMessagePayloadDTO fetch(long selfId, long messageId) {
        ObjectNode params = objectMapper.createObjectNode().put("message_id", messageId);
        JsonNode resp = sender.request(selfId, "get_msg", params, java.time.Duration.ofSeconds(5));

        if (!"ok".equals(resp.path("status").asText()) || !resp.has("data")) {
            throw new RuntimeException("get_msg failed: " + resp.toString());
        }
        JsonNode data = resp.path("data");

        ReplayedMessagePayloadDTO dto = new ReplayedMessagePayloadDTO();
        dto.setMessageId(data.path("message_id").asLong(0));
        dto.setUserId(data.path("user_id").asLong(0));
        dto.setGroupId(data.path("group_id").asLong(0));
        dto.setTimeEpochSec(data.path("time").asLong(0));
        dto.setRawMessage(data.path("raw_message").asText(""));

        JsonNode msg = data.path("message");
        if (msg.isArray()) {
            for (JsonNode seg : msg) {
                String type = seg.path("type").asText("");
                JsonNode d = seg.path("data");
                switch (type) {
                    case "text"  -> dto.addText(d.path("text").asText(""));
                    case "image" -> dto.addImage(SegmentMappers.toImageRef(d));
                    case "mface" -> dto.addMface(SegmentMappers.toMfaceRef(d));
                    case "record"-> dto.addAudio(SegmentMappers.toAudioRef(d));
                    case "video" -> dto.addVideo(SegmentMappers.toVideoRef(d));
                }
            }
        } else {
            CQUtils.fillFromCQ(msg.asText(""), dto);
        }
        return dto;
    }

    // 段映射
    static final class SegmentMappers {

        static ImageRef toImageRef(JsonNode d) {
            String url = textOrNull(d,"url");
            String file= textOrNull(d,"file");
            ImageRef r = new ImageRef();
            if (notBlank(url)) { r.setSourceType(MediaSourceType.URL); r.setSource(url); }
            else { r.setSourceType(MediaSourceType.FILE_ID); r.setSource(file==null?"":file); }
            r.setMime(textOrNull(d,"mime"));
            r.setFilename(textOrNull(d,"filename"));
            r.setMd5(textOrNull(d,"md5"));
            if (d.has("width"))  r.setWidth(d.path("width").asInt());
            if (d.has("height")) r.setHeight(d.path("height").asInt());
            return r;
        }

        static MfaceRef toMfaceRef(JsonNode d) {
            MfaceRef r = new MfaceRef();
            r.setUrl(textOrNull(d, "url"));
            r.setEmojiPackageId(textOrNull(d, "emoji_package_id"));
            r.setEmojiId(textOrNull(d, "emoji_id"));
            r.setKey(textOrNull(d, "key"));
            return r;
        }

        static AudioRef toAudioRef(JsonNode d) {
            String url = textOrNull(d,"url");
            String file= textOrNull(d,"file");
            AudioRef r = new AudioRef();
            if (notBlank(url)) { r.setSourceType(MediaSourceType.URL); r.setSource(url); }
            else { r.setSourceType(MediaSourceType.FILE_ID); r.setSource(file==null?"":file); }
            r.setMime(textOrNull(d,"mime"));
            r.setFilename(textOrNull(d,"filename"));
            if (d.has("duration")) r.setDurationSec(d.path("duration").asInt());
            return r;
        }

        static VideoRef toVideoRef(JsonNode d) {
            String url = textOrNull(d,"url");
            String file= textOrNull(d,"file");
            VideoRef r = new VideoRef();
            if (notBlank(url)) { r.setSourceType(MediaSourceType.URL); r.setSource(url); }
            else { r.setSourceType(MediaSourceType.FILE_ID); r.setSource(file==null?"":file); }
            r.setMime(textOrNull(d,"mime"));
            r.setFilename(textOrNull(d,"filename"));
            if (d.has("duration")) r.setDurationSec(d.path("duration").asInt());
            return r;
        }

        private static String textOrNull(JsonNode d, String k){
            String v = d.path(k).asText(null);
            return (v!=null && !v.isBlank())?v:null;
        }

        private static boolean notBlank(String s){
            return s!=null && !s.isBlank();
        }
    }
}