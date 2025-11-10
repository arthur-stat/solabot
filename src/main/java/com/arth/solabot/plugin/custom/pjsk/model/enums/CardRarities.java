package com.arth.solabot.plugin.custom.pjsk.model.enums;

import lombok.Getter;

public enum CardRarities {

    RARITY_1(1, 1),

    RARITY_2(2, 2),

    RARITY_3(3, 3),

    RARITY_4(4, 4),

    RARITY_BIRTHDAY(1, 5);

    @Getter
    private final int drawQuantity;

    @Getter
    private final int rarity;

    CardRarities(int drawQuantity, int rarity) {
        this.drawQuantity = drawQuantity;
        this.rarity = rarity;
    }

}
