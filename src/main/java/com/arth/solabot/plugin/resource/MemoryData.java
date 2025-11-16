package com.arth.solabot.plugin.resource;

import com.arth.solabot.plugin.custom.pjsk.model.PjskCardInfo;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MemoryData {

    public static ArrayList<PjskCardInfo> cachedCardsNew;//新的卡缓存方法
    public static Map<String, String> alias = new ConcurrentHashMap<>();

    static {
        alias.put("ick", "ick");
        alias.put("星乃一歌", "ick");
        alias.put("一歌", "ick");
        alias.put("小一", "ick");
        alias.put("一", "ick");
        alias.put("炒面面包", "ick");

        alias.put("saki", "saki");
        alias.put("天马咲希", "saki");
        alias.put("咲希", "saki");
        alias.put("小溪", "saki");
        alias.put("溪", "saki");

        alias.put("hnm", "hnm");
        alias.put("望月穗波", "hnm");
        alias.put("穗波", "hnm");
        alias.put("苹果派", "hnm");
        alias.put("萍", "hnm");

        alias.put("shiho", "shiho");
        alias.put("日夜森志步", "shiho");
        alias.put("志步", "shiho");
        alias.put("吸", "shiho");
        alias.put("吸酱", "shiho");

        alias.put("mnr", "mnr");
        alias.put("花里实乃理", "mnr");
        alias.put("实乃理", "mnr");
        alias.put("花", "mnr");
        alias.put("花花", "mnr");
        alias.put("花栗鼠", "mnr");
        alias.put("小实", "mnr");
        alias.put("小花", "mnr");

        alias.put("hrk", "hrk");
        alias.put("桐谷遥", "hrk");
        alias.put("遥", "hrk");
        alias.put("鹅", "hrk");
        alias.put("小遥", "hrk");

        alias.put("airi", "airi");
        alias.put("桃井爱莉", "airi");
        alias.put("爱莉", "airi");
        alias.put("桃", "airi");

        alias.put("szk", "szk");
        alias.put("日夜森雫", "szk");
        alias.put("雫", "szk");

        alias.put("khn", "khn");
        alias.put("小豆泽心羽", "khn");
        alias.put("小豆", "khn");
        alias.put("心羽", "khn");
        alias.put("豆", "khn");
        alias.put("仓鼠", "khn");

        alias.put("an", "an");
        alias.put("白石杏", "an");
        alias.put("杏", "an");
        alias.put("安", "an");

        alias.put("akt", "akt");
        alias.put("东云彰人", "akt");
        alias.put("彰人", "akt");
        alias.put("饼", "akt");
        alias.put("松饼", "akt");

        alias.put("toya", "toya");
        alias.put("青柳冬弥", "toya");
        alias.put("冬弥", "toya");
        alias.put("董秘", "toya");
        alias.put("咖", "toya");

        alias.put("tks", "tks");
        alias.put("天马司", "tks");
        alias.put("司", "tks");
        alias.put("黄桃", "tks");

        alias.put("emu", "emu");
        alias.put("凤笑梦", "emu");
        alias.put("冯小萌", "emu");
        alias.put("小萌", "emu");
        alias.put("姆", "emu");

        alias.put("nene", "nene");
        alias.put("草薙宁宁", "nene");
        alias.put("宁宁", "nene");
        alias.put("宁", "nene");
        alias.put("捏捏", "nene");
        alias.put("捏", "nene");
        alias.put("葡萄柚", "nene");

        alias.put("rui", "rui");
        alias.put("神代类", "rui");
        alias.put("类", "rui");
        alias.put("沈大雷", "rui");

        alias.put("knd", "knd");
        alias.put("k", "knd");
        alias.put("宵崎奏", "knd");
        alias.put("小气走", "knd");
        alias.put("杯面", "knd");
        alias.put("走走", "knd");
        alias.put("奏", "knd");
        alias.put("走", "knd");

        alias.put("mfy", "mfy");
        alias.put("雪", "mfy");
        alias.put("朝比奈真冬", "mfy");
        alias.put("真冬", "mfy");
        alias.put("马", "mfy");

        alias.put("ena", "ena");
        alias.put("enana", "ena");
        alias.put("东云绘名", "ena");
        alias.put("绘名", "ena");
        alias.put("画", "ena");
        alias.put("姐", "ena");

        alias.put("mzk", "mzk");
        alias.put("amia", "mzk");
        alias.put("晓山瑞希", "mzk");
        alias.put("瑞希", "mzk");
        alias.put("糖", "mzk");

        alias.put("miku", "miku");
        alias.put("初音未来", "miku");
        alias.put("未来", "miku");
        alias.put("葱", "miku");

        alias.put("rin", "rin");
        alias.put("镜音铃", "rin");
        alias.put("铃", "rin");
        alias.put("橘", "rin");

        alias.put("len", "len");
        alias.put("镜音连", "len");
        alias.put("连", "len");
        alias.put("蕉", "len");
        alias.put("香蕉", "len");

        alias.put("luka", "luka");
        alias.put("巡音流歌", "luka");
        alias.put("流歌", "luka");
        alias.put("鱼", "luka");

        alias.put("meiko", "meiko");
        alias.put("大姐", "meiko");
        alias.put("酒", "meiko");
        alias.put("美可", "meiko");

        alias.put("kaito", "kaito");
        alias.put("大哥", "kaito");
        alias.put("冰", "kaito");
        alias.put("海人", "kaito");

        alias.put("teto", "teto");
        alias.put("重音", "teto");
        alias.put("钻头", "teto");
        alias.put("铁托", "teto");

        alias.put("猪", "猪");
        alias.put("将军", "猪");
        alias.put("太阳", "猪");
        alias.put("豚", "猪");

        alias.put("夜鹭", "夜鹭");
        alias.put("夜", "夜鹭");

        alias.put("西巴", "西八");
        alias.put("西八", "西八");
        alias.put("兔子", "西八");
        alias.put("兔兔", "西八");
        alias.put("兔", "西八");

        alias.put("kiwi", "kiwi");
        alias.put("几维", "kiwi");

        alias.put("鸟图", "鸟图");
        alias.put("鸟", "鸟图");

        alias.put("企鹅", "企鹅");
        alias.put("咕咕嘎嘎", "企鹅");
    }
}
