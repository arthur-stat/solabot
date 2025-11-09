package com.arth.solabot.plugin.custom.pjsk.func;

import com.arth.solabot.adapter.sender.action.ActionChainBuilder;
import com.arth.solabot.core.bot.dto.ParsedPayloadDTO;
import com.arth.solabot.core.bot.exception.InternalServerErrorException;
import com.arth.solabot.core.infrastructure.LocalData;
import com.arth.solabot.core.infrastructure.database.domain.PjskBinding;
import com.arth.solabot.plugin.custom.Pjsk;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

public final class Mysekai {

    private Mysekai() {
    }

    public static void msm(Pjsk.CoreBeanContext ctx, ParsedPayloadDTO payload, String region) {
        PjskBinding binding = General.queryBinding(ctx, payload.getUserId());
        if (binding == null) {
            ctx.sender().replyText(payload, "数据库中没有查询到你绑定的 pjsk 账号哦");
        } else {
            String pjskId = General.queryPjskIdByRegion(ctx, binding, region);
            if (pjskId == null) {
                ctx.sender().replyText(payload, "该账号没有绑定 " + region + " 服务器的游戏账号");
            } else {
                msmHelper(ctx, payload, pjskId, region);
            }
        }
    }

    public static void msm(Pjsk.CoreBeanContext ctx, ParsedPayloadDTO payload) {
        PjskBinding binding = General.queryBinding(ctx, payload.getUserId());
        if (binding == null) {
            ctx.sender().replyText(payload, "数据库中没有查询到你绑定的 pjsk 账号哦");
        } else {
            General.IdRegionPair pair = General.queryDefaultPjskId(ctx, binding);
            msmHelper(ctx, payload, pair.pjskId(), pair.region());
        }
    }

    // ***** ============= helper ============= *****
    // ***** ============= helper ============= *****
    // ***** ============= helper ============= *****

    public static void msmHelper(Pjsk.CoreBeanContext ctx, ParsedPayloadDTO payload, String pjskId, String region) {
        String updatedTime;

        try {
            Path file = getFilePath(region, pjskId);

            try {
                FileTime timestamp = Files.readAttributes(file, BasicFileAttributes.class).lastModifiedTime();
                updatedTime = ctx.dateTimeFormatter().format(timestamp.toInstant());
            } catch (IOException e) {
                ctx.sender().replyText(payload, "MySekai 数据存在，但获取更新日期失败: 抛出了 IOException");
                throw new InternalServerErrorException("IOException: " + e.getCause().getMessage(), "MySekai 数据存在，但获取更新日期失败: IOException");
            }
        } catch (FileNotFoundException e) {
            ctx.sender().replyText(payload, "服务器上没有找到你的 MySekai 数据，可能是抓包未成功，小概率服务器解析失败，需要根据日志分析");
            return;
        }

        String overviewImgUrl = ctx.apiPaths().buildMysekaiOverviewUrl(region, pjskId);
        String mapImgUrl = ctx.apiPaths().buildMysekaiMapUrl(region, pjskId);
        ActionChainBuilder builder = ctx.actionChainBuilder().create().setReplay(payload.getMessageId())
                .text("MySekai 数据更新于" + updatedTime)
                .image(overviewImgUrl)
                .image(mapImgUrl);

        String json = payload.getMessageType().equals("group") ?
                builder.toGroupJson(payload.getGroupId()) :
                builder.toPrivateJson(payload.getUserId());

        ctx.sender().pushActionJSON(payload.getSelfId(), json);
    }

    private static Path getFilePath(String region, String pjskId) throws FileNotFoundException {
        Path dir = LocalData.PJSK_MYSEKAI_MAP;
        if (!Files.exists(dir) || !Files.isDirectory(dir)) throw new FileNotFoundException("path does not exist");
        Path filePath = dir.resolve(region + "_" + pjskId + ".png");
        if (!Files.exists(filePath)) throw new FileNotFoundException("File not found: " + filePath.getFileName());
        return filePath;
    }

    // ***** ============= uploaded file processor ============= *****
    // ***** ============= uploaded file processor ============= *****
    // ***** ============= uploaded file processor ============= *****
    private static void processMysekaiFile(){

    }
}
