package com.arth.solabot.plugin.custom.pjsk.model.enums;

import lombok.Getter;

public enum CardCharacters {

    //LN
    HOSHINO_ICHIKA(1),
    TENMA_SAKI(2),
    MOCHIZUKI_HONAMI(3),
    HINOMORI_SHINO(4),

    //MMJ
    HANASATO_MINORI(5),
    KIRITANI_HARUKA(6),
    MIMOI_AIRI(7),
    HINOMORI_SHIZUKU(8),

    //VBS
    AZUSAWA_KOHANE(9),
    SHIRAISHI_AN(10),
    SHINONOME_AKITO(11),
    AOYAGI_TOYA(12),

    //WS
    TENMA_TSUKASA(13),
    OTORI_EMU(14),
    KUSANAGI_NENE(15),
    KAMISHIRO_RUI(16),

    //25
    YOISAKI_KANADE(17),
    ASAHINA_MAFUYU(18),
    SHINONOME_ENA(19),
    AKIYAMA_MIZUKI(20),

    //VS
    HATSUNE_MIKU(21),
    KAGAMINE_RIN(22),
    KAGEMINE_REN(23),
    MEGURINE_LUKA(24),
    MEIKO(25),
    KAITO(26);

    @Getter
    private final int charaId;

    CardCharacters(int charaId) {
        this.charaId = charaId;
    }

    public static CardCharacters fromId(int charaId) {
        for (CardCharacters character : CardCharacters.values()) {
            if (character.charaId == charaId) return character;
        }
        return null;
    }

}
