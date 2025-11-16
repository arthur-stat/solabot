package com.arth.solabot.plugin.custom.pjsk.func;

import com.arth.solabot.adapter.controller.ApiPaths;
import com.arth.solabot.adapter.sender.Sender;
import com.arth.solabot.adapter.sender.action.ActionChainBuilder;
import com.arth.solabot.core.bot.dto.ParsedPayloadDTO;
import com.arth.solabot.core.infrastructure.LocalData;
import com.arth.solabot.core.infrastructure.database.domain.PjskBinding;
import com.arth.solabot.core.infrastructure.exception.InternalServerErrorException;
import com.arth.solabot.core.infrastructure.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class Mysekai {

    private final Sender sender;
    private final PjskGeneral pjskGeneral;
    private final ApiPaths apiPaths;
    private final ActionChainBuilder actionChainBuilder;

    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));

    public void msm(ParsedPayloadDTO payload, String region) {
        PjskBinding binding = pjskGeneral.queryBinding(payload.getUserId());
        if (binding == null) {
            sender.replyText(payload, "数据库中没有查询到你绑定的 pjsk 账号哦");
        } else {
            String pjskId = pjskGeneral.queryPjskIdByRegion(binding, region);
            if (pjskId == null) {
                sender.replyText(payload, "该账号没有绑定 " + region + " 服务器的游戏账号");
            } else {
                msmHelper(payload, pjskId, region);
            }
        }
    }

    public void msm(ParsedPayloadDTO payload) {
        PjskBinding binding = pjskGeneral.queryBinding(payload.getUserId());
        if (binding == null) {
            sender.replyText(payload, "数据库中没有查询到你绑定的 pjsk 账号哦");
        } else {
            PjskGeneral.IdRegionPair pair = pjskGeneral.queryDefaultPjskId(binding);
            msmHelper(payload, pair.pjskId(), pair.region());
        }
    }

    // ***** ============= helper ============= *****
    // ***** ============= helper ============= *****
    // ***** ============= helper ============= *****

    public void msmHelper(ParsedPayloadDTO payload, String pjskId, String region) {
        String updatedTime;

        try {
            Path file = getFilePath(region, pjskId);

            try {
                FileTime timestamp = Files.readAttributes(file, BasicFileAttributes.class).lastModifiedTime();
                updatedTime = dateTimeFormatter.format(timestamp.toInstant());
            } catch (IOException e) {
                sender.replyText(payload, "MySekai 数据存在，但获取更新日期失败: 抛出了 IOException");
                throw new InternalServerErrorException("IOException: " + (e.getMessage() != null ? e.getMessage() : e.toString()), "MySekai 数据存在，但获取更新日期失败: IOException");
            }
        } catch (ResourceNotFoundException e) {
            sender.replyText(payload, "服务器上没有找到你的 MySekai 数据，可能是抓包未成功，小概率服务器解析失败，需要根据日志分析");
            return;
        }

        String overviewImgUrl = apiPaths.buildMysekaiOverviewUrl(region, pjskId);
        String mapImgUrl = apiPaths.buildMysekaiMapUrl(region, pjskId);
        ActionChainBuilder builder = actionChainBuilder.create().setReplay(payload.getMessageId())
                .text("MySekai 数据更新于" + updatedTime)
                .image(overviewImgUrl)
                .image(mapImgUrl);

        String json = payload.getMessageType().equals("group") ?
                builder.toGroupJson(payload.getGroupId()) :
                builder.toPrivateJson(payload.getUserId());

        sender.pushActionJSON(payload.getSelfId(), json);
    }

    private Path getFilePath(String region, String pjskId) {
        Path dir = LocalData.PJSK_MYSEKAI_MAP;
        if (!Files.exists(dir) || !Files.isDirectory(dir)) throw new ResourceNotFoundException("path does not exist", "路径不存在");
        Path filePath = dir.resolve(region + "_" + pjskId + ".png");
        if (!Files.exists(filePath)) throw new ResourceNotFoundException("File not found: " + filePath.getFileName(), "文件未找到: " + filePath.getFileName());
        return filePath;
    }

    // ***** ============= uploaded file processor ============= *****
    // ***** ============= uploaded file processor ============= *****
    // ***** ============= uploaded file processor ============= *****

    private static void processMysekaiFile(){
    }
}
