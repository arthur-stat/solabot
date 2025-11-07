package com.arth.solabot.plugin.custom.pjsk.func;

import com.arth.solabot.plugin.custom.Pjsk;
import com.arth.solabot.plugin.custom.pjsk.objects.PjskCardInfo;
import com.arth.solabot.plugin.custom.pjsk.objects.enums.CardAttributes;
import com.arth.solabot.plugin.custom.pjsk.objects.enums.CardCharacters;
import com.arth.solabot.plugin.custom.pjsk.objects.enums.CardRarities;
import com.arth.solabot.plugin.resource.LocalData;
import com.arth.solabot.plugin.resource.MemoryData;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

@Slf4j
public final class LocalResourceData {

    public static JsonNode cards;

    public static String getAssetbundleName(Pjsk.CoreBeanContext ctx, int cardId) throws IOException {
        Path p = LocalData.PJSK_MASTER_DATA_PATH.resolve("master").resolve("cards.json");
        cards = ctx.objectMapper().readTree(p.toFile());
        for (JsonNode card : cards) {
            if (cardId == card.get("id").asInt()) return card.get("assetbundleName").asText();
        }
        return null;
    }


//    public static boolean cached = false;
//    public static PjskCardInfo getCachedCardInfo(Pjsk.CoreBeanContext ctx, int cardId) throws IOException {
//        if (!cached) {
//            log.info("首次使用，正在缓存卡片信息");
//            Path p = ctx.masterDataPath().resolve("master").resolve("cards.json");
//            JsonNode cards = ctx.objectMapper().readTree(p.toFile());
//            MemoryData.cachedCards = new HashMap<>(cards.size());
//            for (JsonNode card : cards) {
//                String assetbundleName = card.get("assetbundleName").asText();
//                CardAttributes attribute = CardAttributes.valueOf(card
//                        .get("attr")
//                        .asText()
//                        .toUpperCase());//sbga
//                CardRarities rarities = CardRarities.valueOf(card.get("cardRarityType")
//                        .asText()
//                        .toUpperCase());
//                MemoryData.cachedCards.put(card.get("id").asInt()-1,new PjskCardInfo(assetbundleName,attribute,rarities));
//            }
//            log.info("卡片信息缓存完成!");
//            cached = true;
//        }
//
//        JsonNode assetbundleNameNode = MemoryData.cachedCards.get(cardId-1);
//        String assetbundleName = assetbundleNameNode.get("assetbundleName").asText();
//        CardAttributes attribute = CardAttributes.valueOf(assetbundleNameNode
//                .get("attr")
//                .asText()
//                .toUpperCase());//sbga
//        CardRarities rarities = CardRarities.valueOf(assetbundleNameNode.get("cardRarityType").asText().toUpperCase());
//        return new PjskCardInfo(assetbundleName, attribute,rarities);
//      if (cardId == card.get("id").asInt()) return card.get("assetbundleName").asText();
//        return MemoryData.cachedCards.get(cardId-1);
//    }

    /**
     * 通过cardId获取cards.json中的卡信息
     */
    public static PjskCardInfo getCachedCardInfo(Pjsk.CoreBeanContext ctx, int cardId) throws IOException {
        if (MemoryData.cachedCardsNew == null) {
            doCacheCardInfoJob(ctx);
        }
        return MemoryData.cachedCardsNew.get(cardId);
    }


    public static void doCacheCardInfoJob(Pjsk.CoreBeanContext ctx) throws IOException {
        log.info("Caching card info for first use...");
        Path p = LocalData.PJSK_MASTER_DATA_PATH.resolve("master").resolve("cards.json");
        JsonNode cards = ctx.objectMapper().readTree(p.toFile());
        MemoryData.cachedCardsNew = new ArrayList<>(cards.size());
        MemoryData.cachedCardsNew.add(null);
        int expectedId = 1;
        for (JsonNode card : cards) {
            int currentId = card.get("id").asInt();
            String assetbundleName = card.get("assetbundleName").asText();
            CardAttributes attribute = CardAttributes.valueOf(card
                    .get("attr")
                    .asText()
                    .toUpperCase());//sbga
            CardRarities rarities = CardRarities.valueOf(card.get("cardRarityType")
                    .asText()
                    .toUpperCase());
            CardCharacters characters = CardCharacters.fromId(card.get("characterId").asInt());
            if (expectedId == currentId) {
                MemoryData.cachedCardsNew.add(new PjskCardInfo(assetbundleName, attribute, rarities, characters));
                expectedId++;
            } else {
                for (int i = 0; i < currentId - expectedId; i++) {
                    MemoryData.cachedCardsNew.add(null);
                }
                MemoryData.cachedCardsNew.add(new PjskCardInfo(assetbundleName, attribute, rarities, characters));
                expectedId = currentId + 1;
            }
        }
        log.info("Card data cached!");
    }

//    private static void tryUpdateCards(Pjsk.CoreBeanContext ctx, String region) throws IOException {
//        Path root = ctx.masterDataPath().resolve(region);
//        if (!Files.exists(root)) Files.createDirectories(root);
//
//        Path versionPath = root.resolve(region).resolve("versions").resolve("current_version.json");
//        JsonNode versionInfo = ctx.objectMapper().readTree(versionPath.toFile());
//        String assetVersion = versionInfo.get("assetVersion").asText();
//
//        String latestAssetVersion = ctx.webClient().get()
//                .uri("")
//                .accept(MediaType.APPLICATION_JSON)
//                .retrieve()
//                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
//                })
//                .map(map -> {
//                    Object command = map.get("assetVersion");
//                    return command == null ? null : command.toString();
//                })
//                .block();
//
//        if (!assetVersion.equals(latestAssetVersion)) {
//
//        }
//
//    }
}
