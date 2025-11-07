package com.arth.solabot.plugin.custom.pjsk.func;

import com.arth.solabot.adapter.sender.action.ActionChainBuilder;
import com.arth.solabot.core.bot.dto.ParsedPayloadDTO;
import com.arth.solabot.core.bot.exception.ExternalServiceErrorException;
import com.arth.solabot.core.bot.exception.InternalServerErrorException;
import com.arth.solabot.core.bot.exception.ResourceNotFoundException;
import com.arth.solabot.core.general.database.domain.PjskBinding;
import com.arth.solabot.core.general.utils.FileUtils;
import com.arth.solabot.plugin.custom.Pjsk;
import com.arth.solabot.plugin.custom.pjsk.objects.PjskCard;
import com.arth.solabot.plugin.custom.pjsk.objects.PjskCardInfo;
import com.arth.solabot.plugin.custom.pjsk.render.ImageRenderer;
import com.arth.solabot.plugin.resource.LocalData;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public final class Suite {

    private Suite() {
    }


    public static void box(Pjsk.CoreBeanContext ctx, ParsedPayloadDTO payload, List<String> args) {
        String id = null;
        String region = null;

        if (!ctx.devel_mode()) {
            long userId = payload.getUserId();
            Long groupId = payload.getGroupId();

            try {
                General.IdRegionPair pair = getIdRegionFromArgs(ctx, userId, args);
                if (pair == null) {
                    ctx.sender().replyText(payload, "没有查询到指定region所绑定的游戏id");
                    return;
                }
                id = pair.pjskId();
                region = pair.region();
            } catch (ResourceNotFoundException e) {
                ctx.sender().replyText(payload, "数据库中没有查询到你绑定的 pjsk 账号哦");
                return;
            }
        }//判断是否为开发模式

        ImageRenderer.Box.BoxDrawMethod boxDrawMethod = ImageRenderer.Box.BoxDrawMethod.CHARA_ID_IN_ASCEND;
        if (!args.isEmpty()) {
            switch (args.get(0)) {
                case "-r":
                    boxDrawMethod = ImageRenderer.Box.BoxDrawMethod.RARITIES_IN_DESCEND;
                    break;
                case "-c":
                default:
                    break;
            }
        }//判断参数，后面得该改

        try {
            ctx.sender().replyText(payload, "已经收到查Box请求，正在生成图片，生成时间较长，请耐心等待");
            JsonNode suiteData;
            if (ctx.devel_mode()) {
                suiteData = getDefaultSuite(ctx);
            } else {
                try {
                    suiteData = getLocalOrRequestAndCacheSuite(ctx, region, id);
                    //suiteData = requestSuite(ctx, region, id);
                } catch (ExternalServiceErrorException e) {
                    ctx.sender().replyText(payload, "向 hrk 请求数据失败，可能还没有在 hrk 上传过 suite");
                    return;
                }
            }
            JsonNode userCardsNode = suiteData.get("userCards");
            ArrayList<PjskCard> pjskCards = new ArrayList<>();
            int counts = userCardsNode.size();
            long startMs = System.currentTimeMillis();
            if (userCardsNode.isArray()) {
                for (JsonNode userCardNode : userCardsNode) {
                    PjskCardInfo info = LocalResourceData.
                            getCachedCardInfo(ctx, userCardNode.get("cardId").asInt());
                    PjskCard card = new PjskCard(userCardNode, info);
                    card.setThumbnails(new ImageRenderer.Card(ctx, card).draw());//慢死了
                    pjskCards.add(card);
                }
            }
            long stopMs = System.currentTimeMillis();
            log.info("Pjsk Box Picture rendering process: {} pictures done,Used {}ms.", counts, stopMs - startMs);
            BufferedImage boxImage = new ImageRenderer.Box(pjskCards, boxDrawMethod).draw(true);//TODO:添加查box参数
            String boxImgUuid = ctx.imageCacheService().cacheImage(boxImage);
            String boxImgUrl = ctx.apiPaths().buildPngUrl(boxImgUuid);
            if (boxImgUuid == null) {
                throw new InternalServerErrorException();
            }

            ActionChainBuilder chainBuilder = ctx.actionChainBuilder().create()
                    .setReplay(payload.getMessageId())
                    .image(boxImgUrl);

            String json = payload.getMessageType().equals("group") ?
                    chainBuilder.toGroupJson(payload.getGroupId()) :
                    chainBuilder.toPrivateJson(payload.getUserId());

            ctx.sender().pushActionJSON(payload.getSelfId(), json);
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
    private static JsonNode getDefaultSuite(Pjsk.CoreBeanContext ctx) {
        Path path = LocalData.PJSK_MASTER_DATA_PATH.resolve("master").resolve("default_suite.json");
        try {
            return ctx.objectMapper().readTree(path.toFile());
        } catch (IOException e) {
            throw new ResourceNotFoundException("default_suite.json not found");
        }
    }

    // ***** ======== FOR OFFLINE MODE ONLY ============ *****
    // ***** ======== FOR OFFLINE MODE ONLY ============ *****


    // ***** ============= account query  ============= *****
    // ***** ============= account query  ============= *****
    // ***** ============= account query  ============= *****

    private static General.IdRegionPair getIdRegionFromArgs(Pjsk.CoreBeanContext ctx, long userId, List<String> args) throws ResourceNotFoundException {
        PjskBinding binding = General.queryBinding(ctx, userId);
        if (binding == null) return null;

        String region;
        if (args == null || args.isEmpty() || !General.isRegionValid(args.get(args.size() - 1))) {
            region = binding.getDefaultServerRegion();
        } else {
            region = args.get(args.size() - 1);
        }

        String pjskId = General.queryPjskIdByRegion(ctx, binding, region);
        if (pjskId == null) {
            return null;
        } else {
            return new General.IdRegionPair(pjskId, region);
        }
    }

    // ***** ============= request helper ============= *****
    // ***** ============= request helper ============= *****
    // ***** ============= request helper ============= *****

    /**
     * 获取本地suite，或从hrkAPI拉取后保存
     *
     * @param ctx
     * @param region
     * @param id
     * @return
     */
    private static JsonNode getLocalOrRequestAndCacheSuite(Pjsk.CoreBeanContext ctx, String region, String id) throws IOException {
        Path suiteFilePath = ctx.localData().getSuitePath(region, id);
        if (Files.exists(suiteFilePath)) {
            try {
                return ctx.objectMapper().readTree(suiteFilePath.toFile());
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                log.error("Getting local suite file failed,try fetch suite file online.");
                return requestSuite(ctx, region, id);
            }
        } else {
            JsonNode node = requestSuite(ctx, region, id);
            try {
                switch (region) {
                    case "cn" -> Files.createDirectories(LocalData.PJSK_SUITE_CN.toAbsolutePath());
                    case "jp" -> Files.createDirectories(LocalData.PJSK_SUITE_JP.toAbsolutePath());
                    case "tw" -> Files.createDirectories(LocalData.PJSK_SUITE_TW.toAbsolutePath());
                }
                FileUtils.getOrCreateFile(suiteFilePath);
                String content = ctx.objectMapper().writeValueAsString(node);
                Files.writeString(suiteFilePath, content);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                log.error("Saving suite file to local failed.Aborting box task.");
                throw new InternalServerErrorException();
            }
            return node;
        }//有无异步保存文件必要
    }

    private static byte[] requestThumbnail(Pjsk.CoreBeanContext ctx, int cardId) {
        String url = ctx.thumbnailApi().replace("{{cardId}}", String.valueOf(cardId));
        return null;
    }

    private static JsonNode requestSuite(Pjsk.CoreBeanContext ctx, String region, String id) {
        String url = ctx.suiteApi().replace("{region}", region).replace("{id}", id);
        return requestUrl(ctx, url);
    }

    private static JsonNode requestSuite(Pjsk.CoreBeanContext ctx, String region, String id, String key) {
        String url = ctx.suiteApi().replace("{region}", region).replace("{id}", id) + "?key=" + key;
        return requestUrl(ctx, url);
    }

    private static JsonNode requestSuite(Pjsk.CoreBeanContext ctx, String region, String id, List<String> keys) {
        String keyParam = String.join(",", keys);
        String url = ctx.suiteApi().replace("{region}", region).replace("{id}", id) + "?key=" + keyParam;
        return requestUrl(ctx, url);
    }

    private static JsonNode requestMysekai(Pjsk.CoreBeanContext ctx, String region, String id) {
        String url = ctx.mysekaiApi().replace("{region}", region).replace("{id}", id);
        return requestUrl(ctx, url);
    }

    private static JsonNode requestMysekai(Pjsk.CoreBeanContext ctx, String region, String id, String key) {
        String url = ctx.mysekaiApi().replace("{region}", region).replace("{id}", id) + "?key=" + key;
        return requestUrl(ctx, url);
    }

    private static JsonNode requestMysekai(Pjsk.CoreBeanContext ctx, String region, String id, List<String> keys) {
        String keyParam = String.join(",", keys);
        String url = ctx.mysekaiApi().replace("{region}", region).replace("{id}", id) + "?key=" + keyParam;
        return requestUrl(ctx, url);
    }

    /**
     * 通用请求方法，基于 WebClient。
     * 会在当前线程阻塞直到获取响应（适合 Spring MVC 环境）。
     */
    private static JsonNode requestUrl(Pjsk.CoreBeanContext ctx, String url) {
        WebClient webClient = ctx.webClient();
        try {
            String responseBody = webClient.get()
                    .uri(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(status -> status == HttpStatus.NOT_FOUND, response ->
                            Mono.error(new ExternalServiceErrorException("status:404, account binding not found"))
                    )
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .flatMap(body -> Mono.error(new IOException(
                                            "Request failed with status code: " + response.statusCode() + "\n" + body)))
                    )
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            if (responseBody == null) throw new IOException("Empty response body for URL: " + url);
            return ctx.objectMapper().readTree(responseBody);
        } catch (Exception e) {
            throw new ExternalServiceErrorException("Failed to request URL: " + url, e.getMessage());
        }
    }
}
