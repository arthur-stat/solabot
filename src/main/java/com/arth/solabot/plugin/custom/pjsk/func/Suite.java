package com.arth.solabot.plugin.custom.pjsk.func;

import com.arth.solabot.adapter.controller.ApiPaths;
import com.arth.solabot.adapter.sender.Sender;
import com.arth.solabot.adapter.sender.action.ActionChainBuilder;
import com.arth.solabot.core.bot.dto.ParsedPayloadDTO;
import com.arth.solabot.core.bot.exception.ExternalServiceErrorException;
import com.arth.solabot.core.bot.exception.InternalServerErrorException;
import com.arth.solabot.core.bot.exception.ResourceNotFoundException;
import com.arth.solabot.core.infrastructure.LocalData;
import com.arth.solabot.core.infrastructure.cache.service.ImageCacheService;
import com.arth.solabot.core.infrastructure.database.domain.PjskBinding;
import com.arth.solabot.core.infrastructure.network.NetworkUtil;
import com.arth.solabot.core.infrastructure.utils.FileUtils;
import com.arth.solabot.plugin.custom.pjsk.model.PjskCard;
import com.arth.solabot.plugin.custom.pjsk.model.PjskCardInfo;
import com.arth.solabot.plugin.custom.pjsk.render.PjskImageRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class Suite {

    private final Sender sender;
    private final PjskGeneral pjskGeneral;
    private final ObjectMapper objectMapper;
    private final ImageCacheService imageCacheService;
    private final ActionChainBuilder actionChainBuilder;
    private final PjskLocalResourceData pjskLocalResourceData;
    private final PjskImageRenderer pjskImageRenderer;
    private final NetworkUtil networkUtil;
    private final LocalData localData;
    private final ApiPaths apiPaths;

    @Value("${app.parameter.plugin.pjsk.devel_mode}")
    private boolean IS_DEV;

    @Value("${app.parameter.plugin.pjsk.external-api.uni.thumbnail-api}")
    private String THUMBNAIL_API;

    @Value("${app.parameter.plugin.pjsk.external-api.hrk.suite-api}")
    private String SUITE_API;

    public void box(ParsedPayloadDTO payload, List<String> args) {
        String id = null;
        String region = null;

        if (!IS_DEV) {
            long userId = payload.getUserId();
            Long groupId = payload.getGroupId();

            try {
                PjskGeneral.IdRegionPair pair = getIdRegionFromArgs(userId, args);
                if (pair == null) {
                    sender.replyText(payload, "没有查询到指定region所绑定的游戏id");
                    return;
                }
                id = pair.pjskId();
                region = pair.region();
            } catch (ResourceNotFoundException e) {
                sender.replyText(payload, "数据库中没有查询到你绑定的 pjsk 账号哦");
                return;
            }
        }//判断是否为开发模式

        PjskImageRenderer.Box.BoxDrawMethod boxDrawMethod = PjskImageRenderer.Box.BoxDrawMethod.CHARA_ID_IN_ASCEND;
        if (!args.isEmpty()) {
            switch (args.get(0)) {
                case "-r":
                    boxDrawMethod = PjskImageRenderer.Box.BoxDrawMethod.RARITIES_IN_DESCEND;
                    break;
                case "-c":
                default:
                    break;
            }
        }//判断参数，后面得该改

        try {
            sender.replyText(payload, "已经收到查Box请求，正在生成图片，生成时间较长，请耐心等待");
            JsonNode suiteData;
            if (IS_DEV) {
                suiteData = getDefaultSuite();
            } else {
                try {
                    suiteData = getLocalOrRequestAndCacheSuite(region, id);
                    //suiteData = requestSuite(ctx, region, id);
                } catch (ExternalServiceErrorException e) {
                    sender.replyText(payload, "向 hrk 请求数据失败，可能还没有在 hrk 上传过 suite");
                    return;
                }
            }
            JsonNode userCardsNode = suiteData.get("userCards");
            ArrayList<PjskCard> pjskCards = new ArrayList<>();
            int counts = userCardsNode.size();
            long startMs = System.currentTimeMillis();
            if (userCardsNode.isArray()) {
                for (JsonNode userCardNode : userCardsNode) {
                    PjskCardInfo info = pjskLocalResourceData.
                            getCachedCardInfo(userCardNode.get("cardId").asInt());
                    PjskCard card = new PjskCard(userCardNode, info);
                    card.setThumbnails(pjskImageRenderer.new PjskCardImg(card).draw());//慢死了
                    pjskCards.add(card);
                }
            }
            long stopMs = System.currentTimeMillis();
            log.info("Pjsk Box Picture rendering process: {} pictures done,Used {}ms.", counts, stopMs - startMs);
            BufferedImage boxImage = pjskImageRenderer.new Box(pjskCards, boxDrawMethod).draw(true);//TODO:添加查box参数
            String boxImgUuid = imageCacheService.cacheImage(boxImage);
            String boxImgUrl = apiPaths.buildPngUrl(boxImgUuid);
            if (boxImgUuid == null) {
                throw new InternalServerErrorException();
            }

            ActionChainBuilder chainBuilder = actionChainBuilder.create()
                    .setReplay(payload.getMessageId())
                    .image(boxImgUrl);

            String json = payload.getMessageType().equals("group") ?
                    chainBuilder.toGroupJson(payload.getGroupId()) :
                    chainBuilder.toPrivateJson(payload.getUserId());

            sender.pushActionJSON(payload.getSelfId(), json);
            //log.info("Box url: {}", boxImgUrl);
            //ctx.sender().sendImage(payload,boxImgUrl);
        } catch (NullPointerException e) {
            throw new InternalServerErrorException("Error in getting user card id");
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new ResourceNotFoundException("Error in getting asset bundle: cards.json not found");
        } catch (ResourceNotFoundException ignored) {
            throw new InternalServerErrorException("Error in getting asset bundle: cards.json not found");
            //???
        }
    }


    // ***** ======== FOR OFFLINE MODE ONLY ============ *****
    // ***** ======== FOR OFFLINE MODE ONLY ============ *****

    //offline_mode=true时调用
    private JsonNode getDefaultSuite() {
        Path path = LocalData.PJSK_MASTER_DATA_PATH.resolve("master").resolve("default_suite.json");
        try {
            return objectMapper.readTree(path.toFile());
        } catch (IOException e) {
            throw new ResourceNotFoundException("default_suite.json not found");
        }
    }

    // ***** ======== FOR OFFLINE MODE ONLY ============ *****
    // ***** ======== FOR OFFLINE MODE ONLY ============ *****


    // ***** ============= account query  ============= *****
    // ***** ============= account query  ============= *****
    // ***** ============= account query  ============= *****

    private PjskGeneral.IdRegionPair getIdRegionFromArgs(long userId, List<String> args) throws ResourceNotFoundException {
        PjskBinding binding = pjskGeneral.queryBinding(userId);
        if (binding == null) return null;

        String region;
        if (args == null || args.isEmpty() || !pjskGeneral.isRegionValid(args.get(args.size() - 1))) {
            region = binding.getDefaultServerRegion();
        } else {
            region = args.get(args.size() - 1);
        }

        String pjskId = pjskGeneral.queryPjskIdByRegion(binding, region);
        if (pjskId == null) {
            return null;
        } else {
            return new PjskGeneral.IdRegionPair(pjskId, region);
        }
    }

    // ***** ============= request helper ============= *****
    // ***** ============= request helper ============= *****
    // ***** ============= request helper ============= *****

    /**
     * 获取本地suite，或从hrkAPI拉取后保存
     *
     * @param region
     * @param id
     * @return
     */
    private JsonNode getLocalOrRequestAndCacheSuite(String region, String id) throws IOException {
        Path suiteFilePath = localData.getSuitePath(region, id);
        if (Files.exists(suiteFilePath)) {
            try {
                return objectMapper.readTree(suiteFilePath.toFile());
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                log.error("Getting local suite file failed,try fetch suite file online.");
                return requestSuite(region, id);
            }
        } else {
            JsonNode node = requestSuite(region, id);
            try {
                switch (region) {
                    case "cn" -> Files.createDirectories(LocalData.PJSK_SUITE_CN.toAbsolutePath());
                    case "jp" -> Files.createDirectories(LocalData.PJSK_SUITE_JP.toAbsolutePath());
                    case "tw" -> Files.createDirectories(LocalData.PJSK_SUITE_TW.toAbsolutePath());
                }
                FileUtils.getOrCreateFile(suiteFilePath);
                String content = objectMapper.writeValueAsString(node);
                Files.writeString(suiteFilePath, content);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                log.error("Saving suite file to local failed.Aborting box task.");
                throw new InternalServerErrorException();
            }
            return node;
        }//有无异步保存文件必要
    }

    private byte[] requestThumbnail(int cardId) {
        String url = THUMBNAIL_API.replace("{{cardId}}", String.valueOf(cardId));
        return null;
    }

    private JsonNode requestSuite(String region, String id) {
        String url = SUITE_API.replace("{region}", region).replace("{id}", id);
        return requestUrl(url);
    }

    private JsonNode requestSuite(String region, String id, String key) {
        String url = SUITE_API.replace("{region}", region).replace("{id}", id) + "?key=" + key;
        return requestUrl(url);
    }

    private JsonNode requestSuite(String region, String id, List<String> keys) {
        String keyParam = String.join(",", keys);
        String url = SUITE_API.replace("{region}", region).replace("{id}", id) + "?key=" + keyParam;
        return requestUrl(url);
    }

    private JsonNode requestMysekai(String region, String id) {
        String url = SUITE_API.replace("{region}", region).replace("{id}", id);
        return requestUrl(url);
    }

    private JsonNode requestMysekai(String region, String id, String key) {
        String url = SUITE_API.replace("{region}", region).replace("{id}", id) + "?key=" + key;
        return requestUrl(url);
    }

    private JsonNode requestMysekai(String region, String id, List<String> keys) {
        String keyParam = String.join(",", keys);
        String url = SUITE_API.replace("{region}", region).replace("{id}", id) + "?key=" + keyParam;
        return requestUrl(url);
    }

    /**
     * 通用请求方法，基于 WebClient。
     * 会在当前线程阻塞直到获取响应（适合 Spring MVC 环境）。
     */
    private JsonNode requestUrl(String url) {
        try {
            String responseBody = networkUtil.getStringWithBrowserHeaders(url, Duration.ofSeconds(30));
            if (responseBody == null) throw new IOException("Empty response body for URL: " + url);
            return objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new ExternalServiceErrorException("Failed to request URL: " + url, e.getMessage());
        }
    }
}
