package com.arth.solabot.plugin.custom.pjsk.model;


import com.arth.solabot.plugin.custom.pjsk.model.enums.CardAttributes;
import com.arth.solabot.plugin.custom.pjsk.model.enums.CardCharacters;
import com.arth.solabot.plugin.custom.pjsk.model.enums.CardRarities;

/**
 * @param assetsBundle  AssetsBundle
 * @param cardAttribute CardAttribute
 * @param rarities      CardRarities
 */
public record PjskCardInfo(String assetsBundle, CardAttributes cardAttribute, CardRarities rarities,
                           CardCharacters characters) {

    public String getAssetsBundle(String left) {
        return assetsBundle;
    }

    public CardAttributes getType() {
        return cardAttribute;
    }

    public CardRarities getRarities() {
        return rarities;
    }

    public CardCharacters getCharacters() {
        return characters;
    }
}

