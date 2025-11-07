package com.arth.solabot.plugin.custom.pjsk.objects;

import com.arth.solabot.plugin.custom.pjsk.objects.enums.CardAttributes;
import com.arth.solabot.plugin.custom.pjsk.objects.enums.CardCharacters;
import com.arth.solabot.plugin.custom.pjsk.objects.enums.CardRarities;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.awt.image.BufferedImage;

@Data
public class PjskCard implements Comparable<PjskCard> {
    @Override
    public int compareTo(PjskCard o) {
        return -Integer.compare(this.rarities.getRarity(), o.rarities.getRarity());
    }//降序排列

    private CardAttributes attributes;
    private CardCharacters cardCharacters;
    private int level;
    private CardRarities rarities;
    private String specialTrainingStatus;
    private int cardId;
    private String assetsbundleName;
    private BufferedImage thumbnails;

    /**
     * 输入单个卡面的JsonNodes，自动转换为PjskCard对象
     *
     * @param userCardNode
     * @param info         CardInfo类
     * @return
     */
    public PjskCard(JsonNode userCardNode, PjskCardInfo info) {
        this.assetsbundleName = info.assetsBundle();
        this.attributes = info.cardAttribute();
        this.cardCharacters = info.getCharacters();
        this.rarities = info.rarities();
        this.level = userCardNode.get("level").asInt();
        this.specialTrainingStatus = userCardNode.get("defaultImage")
                .asText()
                .equals("special_training") ? "after_training" : "normal";
        this.cardId = userCardNode.get("cardId").asInt();
    }

}
